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

class Erc20TokensBalanceFlowInput {
    val tokenAddress: String? = null
    val holderAddress: String? = null
}

data class Erc20TokensBalanceFlowOutput (
    val balance: BigInteger? = null
)

/**
 * Query an address for ERC20 tokens balance.
 *
 * @property tokenAddress the address of the ERC20 contract representing the token for which the balance is queried.
 * @property holderAddress the address of the ERC20 holder whose balance is being queried for.
 * @return the ERC20's total supply.
 */
@Suppress("unused")
class Erc20TokensBalanceFlow: ClientStartableFlow {
    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var evmService: EvmService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("Starting Evm Get Balance Flow...")
        try {
            // Get any of the relevant details from the request here
            val inputs = requestBody.getRequestBodyAs(jsonMarshallingService, Erc20TokensBalanceFlowInput::class.java)

            // Instantiate the erc20 token
            val erc20 = ERC20(Constants.RPC_URL, evmService, inputs.tokenAddress!!,"")

            // Get the balance
            val output = erc20.balanceOf(inputs.holderAddress!!)

            return jsonMarshallingService.format(Erc20TokensBalanceFlowOutput(output))

        } catch (e: Exception) {
            log.error("Error in Evm Get Balance Flow", e)
            throw e
        }
    }
}
