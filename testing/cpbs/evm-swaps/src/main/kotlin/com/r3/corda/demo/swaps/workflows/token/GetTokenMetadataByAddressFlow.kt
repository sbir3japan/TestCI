package com.r3.corda.demo.swaps.workflows.eth2eth

import com.r3.corda.demo.swaps.workflows.Constants
import com.r3.corda.demo.swaps.workflows.ERC20
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.interop.evm.EvmService
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import org.slf4j.LoggerFactory

/**
 * GetTokenMetadataByAddressFlow input parameters
 */
class GetTokenMetadataByAddressFlowInput {
    val address: String? = null
}

/**
 * GetTokenMetadataByAddressFlow output parameters
 */
data class GetTokenMetadataByAddressFlowOutput (
    val name: String? = null,
    val symbol: String? = null,
    val decimals: Byte? = null,
    val address: String? = null
)

/**
 * Get the token metadata by address.
 *
 * @param address the address of the ERC20 token
 *
 * @return the token metadata
 */
@Suppress("unused")
@InitiatingFlow(protocol = "get-token-metadata-by-address")
class GetTokenMetadataByAddressFlow: ClientStartableFlow {
    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var evmService: EvmService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("Starting Evm Get Token metadata by address Flow...")
        try {
            // Get any of the relevant details from the request here
            val inputs = requestBody.getRequestBodyAs(jsonMarshallingService, GetTokenMetadataByAddressFlowInput::class.java)

            // Instantiate the erc20 token
            val erc20 = ERC20(Constants.RPC_URL, evmService, inputs.address!!,"")

            // Get the token name
            val name = erc20.name()

            // Get the token symbol
            val symbol = erc20.symbol()

            // Get the token decimals
            val decimals = erc20.decimals()

            return jsonMarshallingService.format(GetTokenMetadataByAddressFlowOutput(name, symbol, decimals, inputs.address))

        } catch (e: Exception) {
            log.error("Error in Evm Get Metadata Flow", e)
            throw e
        }
    }
}
