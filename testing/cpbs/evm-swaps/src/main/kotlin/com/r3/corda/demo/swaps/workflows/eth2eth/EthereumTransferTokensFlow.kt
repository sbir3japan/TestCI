package com.r3.corda.demo.swaps.workflows.eth2eth
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.interop.evm.EvmService
import net.corda.v5.application.interop.evm.options.TransactionOptions
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import org.slf4j.LoggerFactory
import java.math.BigInteger
import net.corda.v5.application.interop.evm.Parameter
import net.corda.v5.application.interop.evm.Type

class Input {
    val tokenAddress: String? = null
    val buyerAddress: String? = null
    val amount: Int? = null
}


@Suppress("unused")
class EthereumTransferTokensFlow: ClientStartableFlow {
    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val TRANSFER_FUNCTION = "transfer"
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var evmService: EvmService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("Starting Evm Demo Flow...")
        try {
            // Get any of the relevant details from the request here
            val inputs = requestBody.getRequestBodyAs(jsonMarshallingService, Input::class.java)

            val dummyGasNumber = BigInteger("a41c5", 16)
            val transactionOptions = TransactionOptions(
                dummyGasNumber,                 // gasLimit
                0.toBigInteger(),               // value
                20000000000.toBigInteger(),     // maxFeePerGas
                20000000000.toBigInteger(),     // maxPriorityFeePerGas
                "http://localhost:8545",  // rpcUrl
                "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"
            )

            val parameters = listOf(
                Parameter.of("to", Type.ADDRESS, inputs.buyerAddress!!),
                Parameter.of("amount", Type.UINT256, inputs.amount!!.toBigInteger()),
            )
            val hash = this.evmService.transaction(
                "transfer",
                inputs.tokenAddress,
                transactionOptions,
                parameters
            )
            return hash
        } catch (e: Exception) {
            log.error("Error in Evm Demo Flow", e)
            throw e
        }
    }
}