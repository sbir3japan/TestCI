package com.r3.corda.demo.swaps.workflows.swap

import com.r3.corda.demo.swaps.contracts.swap.AssetContract
import com.r3.corda.demo.swaps.contracts.swap.AssetState
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.UtxoLedgerService
import java.time.Duration
import java.time.Instant

/**
 * IssueGenericAssetFlow input parameters.
 */
data class CreateAssetFlowArgs(val assetName: String)

/**
 * Create a new generic asset on the ledger.
 *
 * @param assetName the name of the asset to create
 *
 * @return the transaction id of the created asset that can be used to create the draft transaction.
 */
class IssueGenericAssetFlow : ClientStartableFlow {

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {

        val flowArgs = requestBody.getRequestBodyAs(jsonMarshallingService, CreateAssetFlowArgs::class.java)

        val asset = AssetState(
            flowArgs.assetName,
            memberLookup.myInfo().ledgerKeys.first()
        )

        val txBuilder= ledgerService.createTransactionBuilder()
            .setNotary(notaryLookup.notaryServices.single().name)
            .setTimeWindowUntil(Instant.now() + Duration.ofHours(1))
            .addOutputState(asset)
            .addCommand(AssetContract.AssetCommands.Create())
            .addSignatories(asset.participants)


        val signedTransaction = txBuilder.toSignedTransaction()

        val finalizedTransaction = ledgerService.finalize(signedTransaction, emptyList()).transaction

        return finalizedTransaction.id.toString()
    }
}
