package com.r3.corda.demo.interop.evm

import com.r3.corda.demo.interop.evm.state.FungibleTokenState
import java.math.BigInteger
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.interop.evm.EvmService
import net.corda.v5.application.interop.evm.Parameter
import net.corda.v5.application.interop.evm.Type
import net.corda.v5.application.interop.evm.options.TransactionOptions
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.common.NotaryLookup
import org.slf4j.LoggerFactory
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import java.time.Instant


/**
 * The Evm Demo Flow is solely for demoing access to the EVM from Corda.
 */
@Suppress("unused")
class EvmDemoFlow : ClientStartableFlow {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val TRANSFER_FUNCTION = "sendTokenOne"
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var evmService: EvmService

    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @CordaInject
    lateinit var flowMessaging: FlowMessaging


    @Suspendable
    private fun sendEthereumTransaction(inputs: EvmDemoInput) {
        // Step 1 Build the ethereum transaction
        val dummyGasNumber = BigInteger("a41c5", 16)
        val transactionOptions = TransactionOptions(
            dummyGasNumber,                 // gasLimit
            0.toBigInteger(),               // value
            20000000000.toBigInteger(),     // maxFeePerGas
            20000000000.toBigInteger(),     // maxPriorityFeePerGas
            inputs.rpcUrl!!,                // rpcUrl
            inputs.buyerAddress,          // from
        )

        val parameters = listOf(
            Parameter.of("from", Type.ADDRESS, inputs.buyerAddress!!),
            Parameter.of("to", Type.ADDRESS, inputs.sellerAddress!!),
            Parameter.of("id", Type.UINT256, 1.toBigInteger()),
            Parameter.of("amount", Type.UINT256, inputs.fractionPurchased!!.toBigInteger()),
            Parameter.of("data", Type.BYTES, ""),
        )

        // Step 2.  Call to the Evm to do the asset transfer

        this.evmService.transaction(
            "safeTransferFrom",
            inputs.contractAddress,
            transactionOptions,
            parameters
        )


    }

    @Suspendable
    private fun buildPaymentTransaction(inputs: EvmDemoInput): UtxoSignedTransaction {
        val states = ledgerService.findUnconsumedStatesByExactType(
            FungibleTokenState::class.java,
            100,
            Instant.ofEpochSecond(0)
        )

        val myInfo = memberLookup.myInfo()

        val key = myInfo.ledgerKeys.first()
        val filteredState = states.results.filter { it.state.contractState.linearId == inputs.id }
        // update balances in the state
        val initialBalances = filteredState[0].state.contractState.balances
        val updatedBalances = initialBalances.toMutableMap()
        updatedBalances[key] = updatedBalances[key]!! - inputs.purchasePrice!!.toLong()
        // new state
        val state = FungibleTokenState(
            valuation = filteredState[0].state.contractState.valuation,
            maintainer = filteredState[0].state.contractState.maintainer,
            fractionDigits = filteredState[0].state.contractState.fractionDigits,
            symbol = filteredState[0].state.contractState.symbol,
            balances = updatedBalances,
            participants = filteredState[0].state.contractState.participants
        )

//        val notary = notaryLookup.notaryServices.single()

        val txBuilder = ledgerService.createTransactionBuilder()
//            .setNotary(notary.name)
            .addInputState(filteredState[0].ref)
            .addOutputState(state)

        return txBuilder.toSignedTransaction()
    }


    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("Starting Evm Demo Flow...")
        try {
            // Get any of the relevant details from the request here
            val inputs = requestBody.getRequestBodyAs(jsonMarshallingService, EvmDemoInput::class.java)

            // * Step 1 Build and Send the Ethereum Transaction
            sendEthereumTransaction(inputs)
            // Fetch the state from the ledger
            val signedTransaction = buildPaymentTransaction(inputs)

            // filter states by linearId
            val notary = notaryLookup.notaryServices.single()

            val names = memberLookup.lookup().filter {
                it.memberProvidedContext["corda.notary.service.name"] != notary.name.toString()
            }.map {
                it.name
            }
            // Broadcast to everyone
            val sessions = names.map { flowMessaging.initiateFlow(it) }
            val finalizedSignedTransaction = ledgerService.finalize(signedTransaction, sessions)
            return finalizedSignedTransaction.transaction.id.toString()
        } catch (e: Exception) {
            log.error("Unexpected error while processing the flow", e)
            throw e
        }
    }
}