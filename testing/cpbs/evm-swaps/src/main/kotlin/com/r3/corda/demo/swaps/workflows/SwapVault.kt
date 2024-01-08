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

@Suspendable
class SwapVault(
    private val rpcUrl: String,
    private val evmService: EvmService,
    private val contractAddress: String,
    private val msgSenderPrivateKey: String
) {

    constructor(rpcUrl: String, evmService: EvmService, contractAddress: String) :
            this(rpcUrl, evmService, contractAddress, "0x8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63")

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
            msgSenderPrivateKey,
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

        val receipt = evmService.waitForTransaction(hash, transactionOptions)
        return receipt
    }

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
            msgSenderPrivateKey,
        )

        val parameters = listOf(
            Parameter.of("swapId", Type.STRING, swapId),
            Parameter.of("signatures", Type.ADDRESS_LIST, signatures),
        )

        val hash = evmService.transaction(
            "claimCommitment",
            contractAddress,
            transactionOptions,
            parameters
        )

        val receipt = evmService.waitForTransaction(hash, transactionOptions)
        return receipt
    }

    @Suspendable
    fun commit(swapId: String, recipient: String, signaturesThreshold: BigInteger): Boolean {
        val dummyGasNumber = BigInteger("a41c5", 16)
        val transactionOptions = TransactionOptions(
            dummyGasNumber,                 // gasLimit
            0.toBigInteger(),               // value
            20000000000.toBigInteger(),     // maxFeePerGas
            20000000000.toBigInteger(),     // maxPriorityFeePerGas
            rpcUrl,                // rpcUrl
            contractAddress,          // from
            msgSenderPrivateKey,
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

        val receipt = evmService.waitForTransaction(hash, transactionOptions)
        return receipt.status
    }

    @Suspendable
    fun commit(swapId: String, recipient: String, signaturesThreshold: BigInteger, signatures: List<String>): Boolean {
        val dummyGasNumber = BigInteger("a41c5", 16)
        val transactionOptions = TransactionOptions(
            dummyGasNumber,                 // gasLimit
            0.toBigInteger(),               // value
            20000000000.toBigInteger(),     // maxFeePerGas
            20000000000.toBigInteger(),     // maxPriorityFeePerGas
            rpcUrl,                // rpcUrl
            contractAddress,          // from
            msgSenderPrivateKey,
        )

        val parameters = listOf(
            Parameter.of("swapId", Type.STRING, swapId),
            Parameter.of("recipient", Type.ADDRESS, recipient),
            Parameter.of("signaturesThreshold", Type.UINT256, signaturesThreshold),
            Parameter.of("signatures", Type.ADDRESS_LIST, signatures),
        )

        val hash = evmService.transaction(
            "commit",
            contractAddress,
            transactionOptions,
            parameters
        )

        val receipt = evmService.waitForTransaction(hash, transactionOptions)
        return receipt.status
    }

    @Suspendable
    fun commitWithToken(
        swapId: String,
        tokenAddress: String,
        tokenId: BigInteger,
        amount: BigInteger,
        recipient: String,
        signaturesThreshold: BigInteger
    ): Boolean {
        val dummyGasNumber = BigInteger("a41c5", 16)
        val transactionOptions = TransactionOptions(
            dummyGasNumber,                 // gasLimit
            0.toBigInteger(),               // value
            20000000000.toBigInteger(),     // maxFeePerGas
            20000000000.toBigInteger(),     // maxPriorityFeePerGas
            rpcUrl,                // rpcUrl
            contractAddress,          // from
            msgSenderPrivateKey,
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

        val receipt = evmService.waitForTransaction(hash, transactionOptions)
        return receipt.status
    }

    @Suspendable
    fun commitWithToken(
        swapId: String,
        tokenAddress: String,
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
            msgSenderPrivateKey,
        )

        val parameters = listOf(
            Parameter.of("swapId", Type.STRING, swapId),
            Parameter.of("tokenAddress", Type.ADDRESS, tokenAddress),
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

        val receipt = evmService.waitForTransaction(hash, transactionOptions)
        return receipt
    }

    @Suspendable
    fun commitWithToken(
        swapId: String,
        tokenAddress: String,
        tokenId: BigInteger,
        amount: BigInteger,
        recipient: String,
        signaturesThreshold: BigInteger,
        signatures: List<String>
    ): Boolean {
        val dummyGasNumber = BigInteger("a41c5", 16)
        val transactionOptions = TransactionOptions(
            dummyGasNumber,                 // gasLimit
            0.toBigInteger(),               // value
            20000000000.toBigInteger(),     // maxFeePerGas
            20000000000.toBigInteger(),     // maxPriorityFeePerGas
            rpcUrl,                // rpcUrl
            contractAddress,          // from
            msgSenderPrivateKey,
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

        val receipt = evmService.waitForTransaction(hash, transactionOptions)
        return receipt.status
    }

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
            msgSenderPrivateKey,
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

        val receipt = evmService.waitForTransaction(hash, transactionOptions)
        return receipt
    }

    @Suspendable
    fun commitmentHash(swapId: String): String {
        val parameters = listOf(
            Parameter.of("swapId", Type.STRING, swapId),
        )
        val hash: String = evmService.call(
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

    @Suspendable
    fun recoverSigner(messageHash: String, signature: String): String {
        val parameters = listOf(
            Parameter.of("messageHash", Type.BYTES, messageHash),
            Parameter.of("signature", Type.BYTES, signature),
        )
        val signer: String = evmService.call(
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
