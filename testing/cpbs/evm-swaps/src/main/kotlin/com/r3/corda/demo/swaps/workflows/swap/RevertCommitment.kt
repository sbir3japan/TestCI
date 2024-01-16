package com.r3.corda.demo.swaps.workflows.swap

import com.r3.corda.demo.swaps.workflows.SwapVault
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.interop.evm.EvmService
import net.corda.v5.application.interop.evm.TransactionReceipt
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import org.slf4j.LoggerFactory

/**
 * RevertCommitment input parameters
 */
data class RevertCommitmentInput(
    val transactionId: String,
    val rpcUrl: String,
    val contractAddress: String,
    val msgSenderPrivateKey:String
)

/**
 * RevertCommitment output parameters
 */
data class RevertCommitmentOutput(
    val transactionReceipt: TransactionReceipt
)

/**
 * Revert a commitment on the swap contract returning the asset to its original owner.
 * Any of the participants can call this flow to return the asset to its original owner.
 *
 * @param transactionId the Corda draft transaction ID agreed for the swap
 *
 * @return the transaction hash of the revert commitment transaction
 */
@Suppress("unused")
@InitiatingFlow(protocol = "revert-commitment")
class RevertCommitment : ClientStartableFlow {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var evmService: EvmService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("Starting Evm Revert Commitment Flow...")
        try {
            // Get any of the relevant details from te request here
            val inputs = requestBody.getRequestBodyAs(jsonMarshallingService, RevertCommitmentInput::class.java)

            val transactionReceipt = SwapVault(
                inputs.rpcUrl,
                evmService,
                inputs.contractAddress,
                inputs.msgSenderPrivateKey
            ).revertCommitment(inputs.transactionId)

            return jsonMarshallingService.format(RevertCommitmentOutput(transactionReceipt))
        } catch (e: Exception) {
            log.error("Unexpected error while processing the flow", e)
            throw e
        }
    }
}
