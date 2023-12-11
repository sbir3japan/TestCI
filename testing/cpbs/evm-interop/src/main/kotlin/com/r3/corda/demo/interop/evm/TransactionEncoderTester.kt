package com.r3.corda.demo.interop.evm

import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.interop.evm.EvmService
import net.corda.v5.application.interop.evm.Parameter
import net.corda.v5.application.interop.evm.Type
import net.corda.v5.application.interop.evm.options.TransactionOptions
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import org.slf4j.LoggerFactory

/**
 * The Evm Demo Flow is solely for demoing access to the EVM from Corda.
 */
@Suppress("unused")
class TransactionEncoderTester : ClientStartableFlow {

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
            // Get any of the relevant details from the request here
            val transactionOptions = TransactionOptions(
                1000000000.toBigInteger(),                 // gasLimit
                0.toBigInteger(),               // value
                20000000000.toBigInteger(),     // maxFeePerGas
                1000000000.toBigInteger(),     // maxPriorityFeePerGas
                "http://127.0.0.1:8545",                // rpcUrl
                ""         // from
            )
            val receipt = evmService.transaction(
                "claimCommitment",
                "",
                transactionOptions,
                listOf(
                    Parameter("swapId", Type.STRING, "0x000000"),
                    Parameter("signatures",Type.STRING, "0x000000"),
                )

            )
            return jsonMarshallingService.format(receipt)
        } catch (e: Exception) {
            log.error("Unexpected error while processing the flow", e)
            throw e
        }
    }
}

