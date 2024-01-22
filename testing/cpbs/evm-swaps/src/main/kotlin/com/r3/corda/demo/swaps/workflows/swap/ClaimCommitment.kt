package com.r3.corda.demo.swaps.workflows.swap

import com.r3.corda.demo.swaps.workflows.SwapVault
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.interop.evm.EvmService
import net.corda.v5.application.interop.evm.TransactionReceipt
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import org.slf4j.LoggerFactory

data class ClaimCommitmentInput(
    val transactionId: String,
    val rpcUrl: String,
    val signatures: List<String>?,
    val contractAddress: String,
    val msgSenderPrivateKey:String,
) {
    override fun toString(): String {
        return "ClaimCommitmentInput(transactionId='$transactionId', rpcUrl='$rpcUrl', signatures=$signatures, contractAddress='$contractAddress', msgSenderPrivateKey='$msgSenderPrivateKey')"
    }
}

data class ClaimCommitmentOutput(
    val transactionReceipt: TransactionReceipt,
)

/**
 * The Evm Demo Flow is solely for demoing access to the EVM from Corda.
 */
@Suppress("unused")
class ClaimCommitment : ClientStartableFlow {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)

    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var evmService: EvmService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("Starting Evm Demo Txn Receipt Flow...")
        try {
            // Get any of the relevant details from te request here
            val inputs = requestBody.getRequestBodyAs(jsonMarshallingService, ClaimCommitmentInput::class.java)

            log.info("[DBG] ClaimCommitmentInput: $inputs")

            val transactionReceipt =
                SwapVault(inputs.rpcUrl, evmService, inputs.contractAddress,"")
                    .claimCommitment(inputs.transactionId)

            return jsonMarshallingService.format(ClaimCommitmentOutput(transactionReceipt))
        } catch (e: Exception) {
            log.error("Unexpected error while processing the flow", e)
            throw e
        }
    }
}
