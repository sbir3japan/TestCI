package com.r3.corda.demo.swaps.workflows.eth2eth

import com.r3.corda.demo.swaps.workflows.Constants
import com.r3.corda.demo.swaps.workflows.ERC20
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.interop.evm.EvmService
import net.corda.v5.application.interop.evm.TransactionReceipt
import net.corda.v5.application.interop.evm.options.EvmOptions
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import org.slf4j.LoggerFactory

class GetTransactionReceiptFlowInput {
    val hash: String? = null
}

data class GetTransactionReceiptFlowOutput(
    val transactionReceipt: TransactionReceipt? = null
)

@Suppress("unused")
class GetTransactionReceiptFlow : ClientStartableFlow {
    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var evmService: EvmService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("Starting Evm Get Transaction Receipt Flow...")
        try {
            // Get any of the relevant details from the request here
            val inputs =
                requestBody.getRequestBodyAs(jsonMarshallingService, GetTransactionReceiptFlowInput::class.java)

            // Get the transaction receipt
            val evmOptions = EvmOptions(
                Constants.RPC_URL,
                ""
            )

            val output = evmService.getTransactionReceipt(inputs.hash!!, evmOptions)

            return jsonMarshallingService.format(GetTransactionReceiptFlowOutput(output))

        } catch (e: Exception) {
            log.error("Error in Evm Get Balance Flow", e)
            throw e
        }
    }
}