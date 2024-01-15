package com.r3.corda.demo.swaps.workflows.eth2eth
import com.r3.corda.demo.swaps.workflows.Constants
import com.r3.corda.demo.swaps.workflows.ERC20
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.interop.evm.EvmService
import net.corda.v5.application.interop.evm.TransactionReceipt
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import org.slf4j.LoggerFactory
import java.math.BigInteger

class Erc20TokensTransferFromFlowInput {
    val tokenAddress: String? = null
    val senderAddress: String? = null
    val recipientAddress: String? = null
    val amount: BigInteger? = null
}

data class Erc20TokensTransferFromFlowOutput (
    val transactionReceipt: TransactionReceipt? = null
)
@Suppress("unused")
class Erc20TokensTransferFromFlow: ClientStartableFlow {
    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var evmService: EvmService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("Starting Evm Transfer From Flow...")
        try {
            // Get any of the relevant details from the request here
            val inputs = requestBody.getRequestBodyAs(jsonMarshallingService, Erc20TokensTransferFromFlowInput::class.java)

            // Instantiate the erc20 token
            val erc20 = ERC20(Constants.RPC_URL, evmService, inputs.tokenAddress!!,"")

            // Transfer the tokens
            val output = erc20.transferFrom(inputs.senderAddress!!, inputs.recipientAddress!!, inputs.amount!!)


            return jsonMarshallingService.format(Erc20TokensTransferFromFlowOutput(output))

        } catch (e: Exception) {
            log.error("Error in Evm Transfer From Flow", e)
            throw e
        }
    }
}