package com.r3.corda.demo.interop.evm

import com.r3.corda.demo.interop.evm.state.FungibleTokenState
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import java.math.BigInteger
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.interop.evm.EvmService
import net.corda.v5.application.interop.evm.Parameter
import net.corda.v5.application.interop.evm.Type
import net.corda.v5.application.interop.evm.options.TransactionOptions
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import org.slf4j.LoggerFactory
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens
import net.corda.v5.ledger.utxo.UtxoLedgerService
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

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("Starting Evm Demo Flow...")
        try {
            // Get any of the relevant details from the request here
            val inputs = requestBody.getRequestBodyAs(jsonMarshallingService, EvmDemoInput::class.java)



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


            val hash = this.evmService.transaction(
                "safeTransferFrom",
                inputs.contractAddress,
                transactionOptions,
                parameters
            )
            // Step 2.  Call to the Evm to do the asset transfer

            // fetch the contract state using linear id
            val states = ledgerService.findUnconsumedStatesByExactType(FungibleTokenState::class.java,100, Instant.ofEpochSecond(0))
            // filter states by linearId
            val filteredState = states.results.filter { it.state.contractState.linearId == inputs.id }

            val response = EvmDemoOutput(hash)
            return jsonMarshallingService.format(response)

        } catch (e: Exception) {
            log.error("Unexpected error while processing the flow", e)
            throw e
        }
    }
}