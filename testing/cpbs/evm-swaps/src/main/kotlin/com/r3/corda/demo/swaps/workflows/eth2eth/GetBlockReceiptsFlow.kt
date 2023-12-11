package com.r3.corda.demo.swaps.workflows.eth2eth

import com.r3.corda.demo.swaps.workflows.Constants
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.interop.evm.EvmService
import net.corda.v5.application.interop.evm.TransactionReceipt
import net.corda.v5.application.interop.evm.options.EvmOptions
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import org.slf4j.LoggerFactory
import java.math.BigInteger

class GetBlockReceiptsFlowInput {
    val blockNumber: BigInteger? = null
}

data class GetBlockReceiptsFlowOutput(
    val blockReceipts: List<TransactionReceipt>
)

@Suppress("unused")
class GetBlockReceiptsFlow : ClientStartableFlow {
    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var evmService: EvmService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("Starting Evm Get Block Receipts Flow...")
        try {
            // Get any of the relevant details from the request here
            val inputs = requestBody.getRequestBodyAs(jsonMarshallingService, GetBlockReceiptsFlowInput::class.java)

            val evmOptions = EvmOptions(
                Constants.RPC_URL,
                ""
            )

            val block = evmService.getBlockByNumber(inputs.blockNumber!!, true, evmOptions)

            val transactionReceipts = block.transactions.map {
                evmService.getTransactionReceipt(it, evmOptions)
            }


            return jsonMarshallingService.format(GetBlockReceiptsFlowOutput(transactionReceipts))

        } catch (e: Exception) {
            log.error("Error in Evm Get Block Receipts Flow", e)
            throw e
        }
    }
}