package com.r3.corda.demo.swaps.workflows.eth2eth

import com.r3.corda.demo.swaps.workflows.Constants
import com.r3.corda.demo.swaps.workflows.ERC20
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.interop.evm.Block
import net.corda.v5.application.interop.evm.EvmService
import net.corda.v5.application.interop.evm.options.EvmOptions
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import org.slf4j.LoggerFactory
import java.math.BigInteger

class GetBlockFlowInput {
    val hash: String? = null
    val includeTransactions: Boolean? = null
}

data class GetBlockFlowOutput(
    val block: Block? = null
)

@Suppress("unused")
class GetBlockFlow : ClientStartableFlow {
    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var evmService: EvmService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("Starting Evm Get Block Flow...")
        try {
            // Get any of the relevant details from the request here
            val inputs = requestBody.getRequestBodyAs(jsonMarshallingService, GetBlockFlowInput::class.java)


            val evmOptions = EvmOptions(
                Constants.RPC_URL,
                ""
            )

            // Get the block
            val output = evmService.getBlockByHash(inputs.hash!!, inputs.includeTransactions!!, evmOptions)

            return jsonMarshallingService.format(GetBlockFlowOutput(output))

        } catch (e: Exception) {
            log.error("Error in Evm Get Block Flow", e)
            throw e
        }
    }
}