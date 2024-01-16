package com.r3.corda.demo.swaps.workflows.eth2eth
import com.r3.corda.demo.swaps.workflows.Constants
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.interop.evm.EvmService
import net.corda.v5.application.interop.evm.TransactionObject
import net.corda.v5.application.interop.evm.options.EvmOptions
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import org.slf4j.LoggerFactory

class GetTransactionsFlowInput {
    val hash: String? = null
}

data class GetTransactionsFlowOutput (
    val transaction: TransactionObject? = null
)

/**
 * Get a transaction by its hash
 *
 * @param hash the hash of the transaction
 * @return the ethereum transaction receipt of the transfer.
 */
@Suppress("unused")
class GetTransactionsFlow: ClientStartableFlow {
    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var evmService: EvmService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("Starting Evm Get Transaction Flow...")
        try {
            // Get any of the relevant details from the request here
            val inputs = requestBody.getRequestBodyAs(jsonMarshallingService, GetTransactionsFlowInput::class.java)

            val evmOptions = EvmOptions(
                Constants.RPC_URL,
                ""
            )

            // Get the transaction
            val output = evmService.getTransactionByHash(inputs.hash!!, evmOptions)

            return jsonMarshallingService.format(GetTransactionsFlowOutput(output))

        } catch (e: Exception) {
            log.error("Error in Evm Get Transaction Flow", e)
            throw e
        }
    }
}
