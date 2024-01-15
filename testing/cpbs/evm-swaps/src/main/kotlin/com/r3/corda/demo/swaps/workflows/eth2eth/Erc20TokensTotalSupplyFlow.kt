package com.r3.corda.demo.swaps.workflows.eth2eth
import com.r3.corda.demo.swaps.workflows.Constants
import com.r3.corda.demo.swaps.workflows.ERC20
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.interop.evm.EvmService
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import org.slf4j.LoggerFactory
import java.math.BigInteger

class Erc20TokensTotalSupplyFlowInput {
    val tokenAddress: String? = null
}

data class Erc20TokensTotalSupplyFlowOutput (
    val balance: BigInteger? = null
)
@Suppress("unused")
class Erc20TokensTotalSupplyFlow: ClientStartableFlow {
    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var evmService: EvmService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("Starting Evm Tokens Total Supply Flow...")
        try {
            // Get any of the relevant details from the request here
            val inputs = requestBody.getRequestBodyAs(jsonMarshallingService, Erc20TokensTotalSupplyFlowInput::class.java)

            // Instantiate the erc20 token
            val erc20 = ERC20(Constants.RPC_URL, evmService, inputs.tokenAddress!!,"")

            // Get the total supply
            val output = erc20.totalSupply()

            return jsonMarshallingService.format(Erc20TokensTotalSupplyFlowOutput(output))

        } catch (e: Exception) {
            log.error("Error in Evm Tokens Total Supply Flow", e)
            throw e
        }
    }
}