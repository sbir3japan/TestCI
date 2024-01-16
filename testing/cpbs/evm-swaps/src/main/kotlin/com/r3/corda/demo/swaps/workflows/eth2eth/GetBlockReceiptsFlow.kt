package com.r3.corda.demo.swaps.workflows.eth2eth

import com.r3.corda.demo.swaps.workflows.Constants
import java.math.BigInteger
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.interop.evm.EvmService
import net.corda.v5.application.interop.evm.TransactionReceipt
import net.corda.v5.application.interop.evm.options.EvmOptions
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import org.slf4j.LoggerFactory

class GetBlockReceiptsFlowInput {
    val blockNumber: BigInteger? = null
}

data class GetBlockReceiptsFlowOutput(
    val blockReceipts: List<TransactionReceipt>
)

/**
 * Get all transaction receipts from a block
 *
 * @property blockNumber the number of the block
 * @return the ethereum transaction receipts of a block.
 */
class GetBlockReceiptsFlow : ClientStartableFlow {
    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.trace("Starting Evm Get Block Receipts Flow...")
        try {
            // Get any of the relevant details from the request here
            val inputs = requestBody.getRequestBodyAs(jsonMarshallingService, GetBlockReceiptsFlowInput::class.java)

            val transactionReceipts = flowEngine.subFlow(GetBlockReceiptsSubFlow(inputs.blockNumber!!))

            return jsonMarshallingService.format(GetBlockReceiptsFlowOutput(transactionReceipts))
        } catch (e: Exception) {
            log.error("Error in Evm Get Block Receipts Flow", e)
            throw e
        }
    }
}

/**
 * Get all transaction receipts from a block
 *
 * @property blockNumber the number of the block
 * @return the ethereum transaction receipts of a block.
 */
class GetBlockReceiptsSubFlow(
    private val blockNumber: BigInteger,
) : SubFlow<List<TransactionReceipt>> {
    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var evmService: EvmService

    @Suspendable
    override fun call(): List<TransactionReceipt> {
        log.trace("Starting Evm Get Block Receipts Sub Flow...")
        try {
            val evmOptions = EvmOptions(
                Constants.RPC_URL,
                ""
            )

            val block = evmService.getBlockByNumber(blockNumber, false, evmOptions)

            // NOTE: transactions are hashes only here because fullTransactionObject is false
            val transactionReceipts = block.transactions.map {
                evmService.getTransactionReceipt(it, evmOptions)
            }

            return transactionReceipts
        } catch (e: Exception) {
            log.error("Error in Evm Get Block Receipts Flow", e)
            throw e
        }
    }
}
