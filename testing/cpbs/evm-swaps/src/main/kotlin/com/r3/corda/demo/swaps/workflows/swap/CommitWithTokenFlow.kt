package com.r3.corda.demo.swaps.workflows.swap

import com.r3.corda.demo.swaps.workflows.ERC20
import com.r3.corda.demo.swaps.workflows.SwapVault
import jdk.jfr.Threshold
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


data class CommitWithTokenFlowInput(
    val transactionId: String?,
    val rpcUrl: String?,
    val tokenAddress: String?,
    val recipient: String?,
    val amount: BigInteger,
    val signaturesThreshold: BigInteger,
    val signatures: List<String>?,
    val senderAddress: String,
    val swapProviderAddress: String,
    val msgSenderPrivateKey: String,
)

data class CommitWithTokenFlowOutput(
    val transactionReceipt: TransactionReceipt

    )

/**
 * The Evm Demo Flow is solely for demoing access to the EVM from Corda.
 */
@Suppress("unused")
class CommitWithTokenFlow : ClientStartableFlow {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)

    }


    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var evmService: EvmService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("Starting Commitment With Token Flow Flow...")
        try {
            // Get any of the relevant details from te request here
            val inputs = requestBody.getRequestBodyAs(jsonMarshallingService, CommitWithTokenFlowInput::class.java)
            val erc20 = ERC20(inputs.rpcUrl!!, evmService, inputs.tokenAddress!!,"")

            val swapVault = SwapVault(
                inputs.rpcUrl,
                evmService,
                inputs.swapProviderAddress,
                ""
            )

            erc20.approve(inputs.swapProviderAddress, inputs.amount)

            val transactionReceipt = swapVault.commitWithToken(
                inputs.transactionId!!,
                inputs.tokenAddress,
                inputs.amount,
                inputs.recipient!!,
                inputs.signaturesThreshold,
                inputs.signatures!!
            )


            return jsonMarshallingService.format(CommitWithTokenFlowOutput(transactionReceipt))

        } catch (e: Exception) {
            log.error("Unexpected error while processing the flow", e)
            throw e
        }
    }
}
