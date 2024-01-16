package com.r3.corda.demo.swaps.workflows.swap

import com.r3.corda.demo.swaps.workflows.ERC20
import com.r3.corda.demo.swaps.workflows.SwapVault
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.interop.evm.EvmService
import net.corda.v5.application.interop.evm.TransactionReceipt
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import org.slf4j.LoggerFactory
import java.math.BigInteger

/**
 * CommitWithTokenFlow input parameters
 */
data class CommitWithTokenFlowInput(
    val transactionId: String,
    val rpcUrl: String,
    val tokenAddress: String,
    val recipient: String,
    val amount: BigInteger,
    val signaturesThreshold: BigInteger,
    val signers: List<String>,
    val swapProviderAddress: String,
    val msgSenderPrivateKey: String,
)

/**
 * CommitWithTokenFlow output parameters
 */
data class CommitWithTokenFlowOutput(
    val transactionReceipt: TransactionReceipt
)

/**
 * Commits an ERC20 asset to the swap contract.
 *
 * @param transactionId the Corda draft transaction ID agreed for the swap
 * @param rpcUrl the RPC URL of the Ethereum node
 * @param tokenAddress the address of the ERC20 token
 * @param recipient the EVM recipient address of the ERC20 token
 * @param amount the amount of the ERC20 token
 * @param signaturesThreshold the minimum number of oracle signatures required for the swap (swap parameter)
 * @param signers the EVM identity of the Oracles whose signature will be requested to (proof of notarization)
 *                   e.g.: [ "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266", "0x70997970C51812dc3A010C7d01b50e0d17dc79C8" ]
 * @param swapProviderAddress the EVM deployment address of the swap contract
 * @param msgSenderPrivateKey the private key of the EVM identity that will be used to sign the transaction
 *
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

            val erc20 = ERC20(inputs.rpcUrl, evmService, inputs.tokenAddress, inputs.msgSenderPrivateKey)

            val swapVault = SwapVault(
                inputs.rpcUrl,
                evmService,
                inputs.swapProviderAddress,
                inputs.msgSenderPrivateKey
            )

            erc20.approve(inputs.swapProviderAddress, inputs.amount)

            val transactionReceipt = swapVault.commitWithToken(
                inputs.transactionId,
                inputs.tokenAddress,
                inputs.amount,
                inputs.recipient,
                inputs.signaturesThreshold,
                inputs.signers
            )

            return jsonMarshallingService.format(CommitWithTokenFlowOutput(transactionReceipt))

        } catch (e: Exception) {
            log.error("Unexpected error while processing the flow", e)
            throw e
        }
    }
}
