package com.r3.corda.demo.swaps.workflows.eth2eth

import com.r3.corda.demo.swaps.workflows.Constants
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.interop.evm.Block
import net.corda.v5.application.interop.evm.EvmService
import net.corda.v5.application.interop.evm.options.EvmOptions
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import org.slf4j.LoggerFactory

class GetBlockByHashFlowInput {
    val hash: String? = null
    val includeTransactions: Boolean? = null
}

data class GetBlockByHashFlowOutput(
    val block: Block? = null
)

/**
 * Get a block by its hash
 *
 * @param hash the hash of the block to request
 * @param includeTransactions whether to include all transactions in the block (not supported!) or not
 * @return the ethereum block with the given hash.
 */
class GetBlockByHashFlow : ClientStartableFlow {
    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    override fun call(requestBody: ClientRequestBody): String {
        log.trace("Starting Evm Get Block Client Flow...")

        val inputs = requestBody.getRequestBodyAs(jsonMarshallingService, GetBlockByHashFlowInput::class.java)
        val output = flowEngine.subFlow(GetBlockByHashSubFlow(inputs.hash!!, inputs.includeTransactions!!))

        return jsonMarshallingService.format(GetBlockByHashFlowOutput(output))
    }
}

/**
 * Get a block by its hash
 *
 * @param hash the hash of the block to request
 * @param includeTransactions whether to include all transactions in the block (not supported!) or not
 * @return the ethereum block with the given hash.
 */
@Suppress("unused")
class GetBlockByHashSubFlow(
    private val hash: String,
    private val includeTransactions: Boolean
) : SubFlow<Block> {
    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var evmService: EvmService

    @Suspendable
    override fun call(): Block {
        log.trace("Starting Evm Get Block Sub Flow...")
        try {
            val evmOptions = EvmOptions(
                Constants.RPC_URL,
                ""
            )
            // Get the block
            return evmService.getBlockByHash(hash, includeTransactions, evmOptions)
        } catch (e: Exception) {
            log.error("Error in Evm Get Block Sub Flow", e)
            throw e
        }
    }
}
