package com.r3.corda.demo.swaps.workflows.swap

import com.r3.corda.demo.swaps.contracts.swap.AssetState
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.UtxoLedgerService
import java.time.Instant
import java.util.UUID

/**
 * IssueGenericAssetFlow input parameters.
 */
data class CreateAssetFlowOutput(val assetName: String, val linearId: UUID)

/**
 * Query the generic asset state on the ledger.
 */
@Suppress("unused")
@InitiatingFlow(protocol = "generic-asset-query")
class QueryGenericAssetFlow : ClientStartableFlow {

    companion object {
        data class Args(val assetName: String)
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {

        val assets = ledgerService.findUnconsumedStatesByExactType(AssetState::class.java,100, Instant.now())
        val formattedOutput = assets.results.map {
            CreateAssetFlowOutput(
                it.state.contractState.assetName,
                it.state.contractState.linearId
            )
        }
        return jsonMarshallingService.format(formattedOutput)
    }
}
