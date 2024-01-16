package com.r3.corda.demo.swaps.workflows

import net.corda.v5.application.interop.evm.EvmService
import net.corda.v5.application.interop.evm.Parameter
import net.corda.v5.application.interop.evm.TransactionReceipt
import net.corda.v5.application.interop.evm.Type
import net.corda.v5.application.interop.evm.options.CallOptions
import net.corda.v5.application.interop.evm.options.EvmOptions
import net.corda.v5.application.interop.evm.options.TransactionOptions
import net.corda.v5.base.annotations.Suspendable
import java.math.BigInteger

/**
 * Provides access to the SwapVault.sol smart contract.
 *
 * @param rpcUrl the url of the EVM's JSON RPC Gateway as protocol://url:port (i.e.: http://localhost:8545)
 * @param evmService an EVM Service instance to rely on for Corda - EVM interop
 * @param contractAddress the deployment address of the SwapVault contract address
 * @param privateKey the private key that will be used to sign the transactions for this contract instance
 */
@Suspendable
class SwapVault(
    private val rpcUrl: String,
    private val evmService: EvmService,
    private val contractAddress: String,
    private val privateKey: String,
) {

    /**
     * Transfers a committed asset to its recipient. Only the committer, the owner of the asset, can call this function.
     *
     * @param swapId the committed asset identifier
     */
    @Suspendable
    fun claimCommitment(swapId: String): TransactionReceipt {
        val dummyGasNumber = BigInteger("a41c5", 16)
        val transactionOptions = TransactionOptions(
            dummyGasNumber,                 // gasLimit
            0.toBigInteger(),               // value
            20000000000.toBigInteger(),     // maxFeePerGas
            20000000000.toBigInteger(),     // maxPriorityFeePerGas
            rpcUrl,                // rpcUrl
            contractAddress,          // from
            privateKey
        )

        val parameters = listOf(
            Parameter.of("swapId", Type.STRING, swapId),
        )

        val hash = evmService.transaction(
            "claimCommitment",
            contractAddress,
            transactionOptions,
            parameters
        )

        return evmService.waitForTransaction(hash, transactionOptions)
    }

    /**
     * Transfers a committed asset to its recipient. Callable by the recipient of the committed asset by providing
     * proof of notarization with support from the selected (at commit) Oracles.
     *
     * @param swapId the committed asset identifier
     * @param signatures oracles' signatures (proof of notarization)
     */
    @Suspendable
    fun claimCommitment(swapId: String, signatures: List<String>): TransactionReceipt {
        val dummyGasNumber = BigInteger("a41c5", 16)
        val transactionOptions = TransactionOptions(
            dummyGasNumber,                 // gasLimit
            0.toBigInteger(),               // value
            20000000000.toBigInteger(),     // maxFeePerGas
            20000000000.toBigInteger(),     // maxPriorityFeePerGas
            rpcUrl,                // rpcUrl
            contractAddress,          // from
            privateKey
        )

        val parameters = listOf(
            Parameter.of("swapId", Type.STRING, swapId),
            Parameter.of("signatures", Type.BYTE_ARRAY, signatures),
        )

        val hash = evmService.transaction(
            "claimCommitment",
            contractAddress,
            transactionOptions,
            parameters
        )

        return evmService.waitForTransaction(hash, transactionOptions)
    }

    /**
     * Base commit function, not storing an asset.
     *
     * @param swapId the commit unique swap identifier
     * @param recipient the recipient of the swap
     * @param signaturesThreshold
     */
    @Suspendable
    fun commit(swapId: String, recipient: String, signaturesThreshold: BigInteger): TransactionReceipt {
        val dummyGasNumber = BigInteger("a41c5", 16)
        val transactionOptions = TransactionOptions(
            dummyGasNumber,                 // gasLimit
            0.toBigInteger(),               // value
            20000000000.toBigInteger(),     // maxFeePerGas
            20000000000.toBigInteger(),     // maxPriorityFeePerGas
            rpcUrl,                // rpcUrl
            contractAddress,          // from
            privateKey
        )

        val parameters = listOf(
            Parameter.of("swapId", Type.STRING, swapId),
            Parameter.of("recipient", Type.ADDRESS, recipient),
            Parameter.of("signaturesThreshold", Type.UINT256, signaturesThreshold),
        )

        val hash = evmService.transaction(
            "commit",
            contractAddress,
            transactionOptions,
            parameters
        )

        return evmService.waitForTransaction(hash, transactionOptions)
    }

    /**
     * Base commit function, not storing an asset.
     *
     * @param swapId the commit unique swap identifier
     * @param recipient the recipient of the swap
     * @param signaturesThreshold the minimum number of oracle signatures required for the swap (swap parameter)
     * @param signers the identities (EVM addresses) of the Oracles that can witness by signature
     */
    @Suspendable
    fun commit(swapId: String, recipient: String, signaturesThreshold: BigInteger, signers: List<String>): TransactionReceipt {
        val dummyGasNumber = BigInteger("a41c5", 16)
        val transactionOptions = TransactionOptions(
            dummyGasNumber,                 // gasLimit
            0.toBigInteger(),               // value
            20000000000.toBigInteger(),     // maxFeePerGas
            20000000000.toBigInteger(),     // maxPriorityFeePerGas
            rpcUrl,                // rpcUrl
            contractAddress,          // from
            privateKey
        )

        val parameters = listOf(
            Parameter.of("swapId", Type.STRING, swapId),
            Parameter.of("recipient", Type.ADDRESS, recipient),
            Parameter.of("signaturesThreshold", Type.UINT256, signaturesThreshold),
            Parameter.of("signers", Type.ADDRESS_LIST, signers),
        )

        val hash = evmService.transaction(
            "commit",
            contractAddress,
            transactionOptions,
            parameters
        )

        return evmService.waitForTransaction(hash, transactionOptions)
    }

    /**
     * Commit an ERC20 asset to the swap contract.
     *
     * @param swapId the commit unique swap identifier
     * @param tokenAddress the asset's (ERC20) deployment address on the EVM network
     * @param amount the amount of tokens to transfer to commit for the swap
     * @param recipient the recipient of the ERC20 asset (should claim complete).
     * @param signaturesThreshold the minimum number of oracle signatures required for the swap (swap parameter)
     */
    @Suspendable
    fun commitWithToken(
        swapId: String,
        tokenAddress: String,
        amount: BigInteger,
        recipient: String,
        signaturesThreshold: BigInteger
    ): TransactionReceipt {
        val dummyGasNumber = BigInteger("a41c5", 16)
        val transactionOptions = TransactionOptions(
            dummyGasNumber,                 // gasLimit
            0.toBigInteger(),               // value
            20000000000.toBigInteger(),     // maxFeePerGas
            20000000000.toBigInteger(),     // maxPriorityFeePerGas
            rpcUrl,                // rpcUrl
            contractAddress,          // from
            privateKey
        )

        val parameters = listOf(
            Parameter.of("swapId", Type.STRING, swapId),
            Parameter.of("tokenAddress", Type.ADDRESS, tokenAddress),
            Parameter.of("amount", Type.UINT256, amount),
            Parameter.of("recipient", Type.ADDRESS, recipient),
            Parameter.of("signaturesThreshold", Type.UINT256, signaturesThreshold),
        )

        val hash = evmService.transaction(
            "commitWithToken",
            contractAddress,
            transactionOptions,
            parameters
        )

        return evmService.waitForTransaction(hash, transactionOptions)
    }

    /**
     * Commit an ERC20 asset to the swap contract.
     *
     * @param swapId the commit unique swap identifier
     * @param tokenAddress the asset's (ERC20) deployment address on the EVM network
     * @param amount the amount of tokens to transfer to commit for the swap
     * @param recipient the recipient of the ERC20 asset (should claim complete).
     * @param signaturesThreshold the minimum number of oracle signatures required for the swap (swap parameter)
     * @param signers the identities (EVM addresses) of the Oracles that can witness by signature
     */
    @Suspendable
    fun commitWithToken(
        swapId: String,
        tokenAddress: String,
        amount: BigInteger,
        recipient: String,
        signaturesThreshold: BigInteger,
        signers: List<String>
    ): TransactionReceipt {
        val dummyGasNumber = BigInteger("a41c5", 16)
        val transactionOptions = TransactionOptions(
            dummyGasNumber,                 // gasLimit
            0.toBigInteger(),               // value
            20000000000.toBigInteger(),     // maxFeePerGas
            20000000000.toBigInteger(),     // maxPriorityFeePerGas
            rpcUrl,                // rpcUrl
            contractAddress,          // from
            privateKey
        )

        val parameters = listOf(
            Parameter.of("swapId", Type.STRING, swapId),
            Parameter.of("tokenAddress", Type.ADDRESS, tokenAddress),
            Parameter.of("amount", Type.UINT256, amount),
            Parameter.of("recipient", Type.ADDRESS, recipient),
            Parameter.of("signaturesThreshold", Type.UINT256, signaturesThreshold),
            Parameter.of("signers", Type.ADDRESS_LIST, signers),
        )

        val hash = evmService.transaction(
            "commitWithToken",
            contractAddress,
            transactionOptions,
            parameters
        )

        return evmService.waitForTransaction(hash, transactionOptions)
    }

    /**
     * Commit an ERC721/ERC1155 asset to the swap contract.
     *
     * @param swapId the commit unique swap identifier
     * @param tokenAddress the asset's (ERC20) deployment address on the EVM network
     * @param tokenId the token ID of the ERC721/ERC1155 token to commit for the swap
     * @param amount the amount of tokens to transfer to commit for the swap
     * @param recipient the recipient of the ERC20 asset (should claim complete).
     * @param signaturesThreshold the minimum number of oracle signatures required for the swap (swap parameter)
     */
    @Suspendable
    fun commitWithToken(
        swapId: String,
        tokenAddress: String,
        tokenId: BigInteger,
        amount: BigInteger,
        recipient: String,
        signaturesThreshold: BigInteger
    ): TransactionReceipt {
        val dummyGasNumber = BigInteger("a41c5", 16)
        val transactionOptions = TransactionOptions(
            dummyGasNumber,                 // gasLimit
            0.toBigInteger(),               // value
            20000000000.toBigInteger(),     // maxFeePerGas
            20000000000.toBigInteger(),     // maxPriorityFeePerGas
            rpcUrl,                // rpcUrl
            contractAddress,          // from
            privateKey
        )

        val parameters = listOf(
            Parameter.of("swapId", Type.STRING, swapId),
            Parameter.of("tokenAddress", Type.ADDRESS, tokenAddress),
            Parameter.of("tokenId", Type.UINT256, tokenId),
            Parameter.of("amount", Type.UINT256, amount),
            Parameter.of("recipient", Type.ADDRESS, recipient),
            Parameter.of("signaturesThreshold", Type.UINT256, signaturesThreshold),
        )

        val hash = evmService.transaction(
            "commitWithToken",
            contractAddress,
            transactionOptions,
            parameters
        )

        return evmService.waitForTransaction(hash, transactionOptions)
    }

    /**
     * Commit an ERC721/ERC1155 asset to the swap contract.
     *
     * @param swapId the commit unique swap identifier
     * @param tokenAddress the asset's (ERC20) deployment address on the EVM network
     * @param tokenId the token ID of the ERC721/ERC1155 token to commit for the swap
     * @param amount the amount of tokens to transfer to commit for the swap
     * @param recipient the recipient of the ERC20 asset (should claim complete).
     * @param signaturesThreshold the minimum number of oracle signatures required for the swap (swap parameter)
     * @param signaturesThreshold the minimum number of oracle signatures required for the swap (swap parameter)
     */
    @Suspendable
    fun commitWithToken(
        swapId: String,
        tokenAddress: String,
        tokenId: BigInteger,
        amount: BigInteger,
        recipient: String,
        signaturesThreshold: BigInteger,
        signatures: List<String>
    ): TransactionReceipt {
        val dummyGasNumber = BigInteger("a41c5", 16)
        val transactionOptions = TransactionOptions(
            dummyGasNumber,                 // gasLimit
            0.toBigInteger(),               // value
            20000000000.toBigInteger(),     // maxFeePerGas
            20000000000.toBigInteger(),     // maxPriorityFeePerGas
            rpcUrl,                // rpcUrl
            contractAddress,          // from
            privateKey
        )

        val parameters = listOf(
            Parameter.of("swapId", Type.STRING, swapId),
            Parameter.of("tokenAddress", Type.ADDRESS, tokenAddress),
            Parameter.of("tokenId", Type.UINT256, tokenId),
            Parameter.of("amount", Type.UINT256, amount),
            Parameter.of("recipient", Type.ADDRESS, recipient),
            Parameter.of("signaturesThreshold", Type.UINT256, signaturesThreshold),
            Parameter.of("signatures", Type.ADDRESS_LIST, signatures),
        )

        val hash = evmService.transaction(
            "commitWithToken",
            contractAddress,
            transactionOptions,
            parameters
        )

        return evmService.waitForTransaction(hash, transactionOptions)
    }

    /**
     * Revert a committed asset to the original owner (no restrictions)
     *
     * @param swapId the id of the commitment to revert
     */
    @Suspendable
    fun revertCommitment(swapId: String): TransactionReceipt {
        val dummyGasNumber = BigInteger("a41c5", 16)
        val transactionOptions = TransactionOptions(
            dummyGasNumber,                 // gasLimit
            0.toBigInteger(),               // value
            20000000000.toBigInteger(),     // maxFeePerGas
            20000000000.toBigInteger(),     // maxPriorityFeePerGas
            rpcUrl,                // rpcUrl
            contractAddress,          // from
            privateKey
        )

        val parameters = listOf(
            Parameter.of("swapId", Type.STRING, swapId),
        )

        val hash = evmService.transaction(
            "revertCommitment",
            contractAddress,
            transactionOptions,
            parameters
        )

        return evmService.waitForTransaction(hash, transactionOptions)
    }

    /**
     * Return the unique hash of the committed asset by its commitment ID.
     */
    @Suspendable
    fun commitmentHash(swapId: String): String {
        val parameters = listOf(
            Parameter.of("swapId", Type.STRING, swapId),
        )
        val hash = evmService.call(
            "commitmentHash",
            contractAddress,
            CallOptions(
                EvmOptions(
                    rpcUrl,
                    ""
                )
            ),
            Type.BYTES,
            parameters
        )

        return hash
    }

    /**
     * return the signer of a signature.
     */
    @Suspendable
    fun recoverSigner(messageHash: String, signature: String): String {
        val parameters = listOf(
            Parameter.of("messageHash", Type.BYTES, messageHash),
            Parameter.of("signature", Type.BYTES, signature),
        )
        val signer = evmService.call(
            "recoverSigner",
            contractAddress,
            CallOptions(
                EvmOptions(
                    rpcUrl,
                    ""
                )
            ),
            Type.ADDRESS,
            parameters
        )

        return signer
    }
}
