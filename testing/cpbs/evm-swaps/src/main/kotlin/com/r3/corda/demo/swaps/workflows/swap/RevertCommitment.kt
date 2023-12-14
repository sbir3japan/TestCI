package com.r3.corda.demo.swaps.workflows.swap

import com.r3.corda.demo.swaps.workflows.SwapVault
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.interop.evm.EvmService
import net.corda.v5.application.interop.evm.TransactionReceipt
import net.corda.v5.application.interop.evm.options.EvmOptions
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.annotations.Suspendable
import org.slf4j.LoggerFactory


data class RevertCommitmentInput(
    val transactionId: String?,
    val rpcUrl: String?,
    val contractAddress: String?

)

data class RevertCommitmentOutput(
    val transactionReceipt: String?,
    )

/**
 * The Evm Demo Flow is solely for demoing access to the EVM from Corda.
 */
@Suppress("unused")
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
                inputs.rpcUrl!!,
                evmService,
                inputs.contractAddress!!
            ).revertCommitment(inputs.transactionId!!)
            return jsonMarshallingService.format(RevertCommitmentOutput(transactionReceipt.transactionHash))
        } catch (e: Exception) {
            log.error("Unexpected error while processing the flow", e)
            throw e
        }
    }
}

