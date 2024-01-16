package com.r3.corda.demo.swaps.workflows

import java.math.BigInteger
import net.corda.v5.application.interop.evm.EvmService
import net.corda.v5.application.interop.evm.Parameter
import net.corda.v5.application.interop.evm.TransactionReceipt
import net.corda.v5.application.interop.evm.Type
import net.corda.v5.application.interop.evm.options.CallOptions
import net.corda.v5.application.interop.evm.options.EvmOptions
import net.corda.v5.application.interop.evm.options.TransactionOptions
import net.corda.v5.base.annotations.Suspendable

/**
 * This class provides an interface for interacting with an ERC20 smart contract deployed on an EVM-compatible blockchain.
 *
 * @param rpcUrl The URL of the EVM's JSON RPC Gateway
 * @param evmService An EVM Service instance for interacting with the EVM
 * @param contractAddress The deployment address of the ERC20 contract
 * @param privateKey The private key of the account that will be used to sign transactions for this contract instance
 */
@Suspendable
class ERC20(
    private val rpcUrl: String,
    private val evmService: EvmService,
    private val contractAddress: String,
    private val privateKey: String,
) {

    /**
     * Gets the name of the ERC20 token.
     *
     * @return The name of the token as a string
     */
    @Suspendable
    fun name(): String {
        return evmService.call(
            "name",
            contractAddress,
            CallOptions(
                EvmOptions(
                    rpcUrl,
                    ""
                )
            ),
            Type.STRING,
        )
    }

    /**
     * Gets the symbol of the ERC20 token.
     *
     * @return The symbol of the token as a string
     */
    @Suspendable
    fun symbol(): String {
        return evmService.call(
            "symbol",
            contractAddress,
            CallOptions(
                EvmOptions(
                    rpcUrl,
                    ""
                )
            ),
            Type.STRING,
        )
    }

    /**
     * Gets the number of decimal places used for the ERC20 token.
     *
     * @return The decimals of the token as a byte
     */
    @Suspendable
    fun decimals(): Byte {
        return evmService.call(
            "decimals",
            contractAddress,
            CallOptions(
                EvmOptions(
                    rpcUrl,
                    ""
                )
            ),
            Type.UINT8
        )
    }

    /**
     * Gets the total supply of the ERC20 token.
     *
     * @return The total supply of the token as a BigInteger
     */
    @Suspendable
    fun totalSupply(): BigInteger {
        return evmService.call(
            "totalSupply",
            contractAddress,
            CallOptions(
                EvmOptions(
                    rpcUrl,
                    ""
                )
            ),
            Type.UINT256,
        )
    }

    /**
     * Gets the balance of an account for the ERC20 token.
     *
     * @param owner The address of the account to check the balance for
     * @return The balance of the account for the token as a BigInteger
     */
    @Suspendable
    fun balanceOf(owner: String): BigInteger {
        return evmService.call(
            "balanceOf",
            contractAddress,
            CallOptions(
                EvmOptions(
                    rpcUrl,
                    ""
                )
            ),
            Type.UINT256,
            Parameter.of("owner", Type.ADDRESS, owner),
        )
    }

    /**
     * Transfers a specified amount of ERC20 tokens from the calling account to another account.
     *
     * @param to The address of the recipient of the tokens
     * @param value The amount of tokens to transfer
     * @return The transaction receipt for the transfer operation
     */
    @Suspendable
    fun transfer(to: String, value: BigInteger): TransactionReceipt {
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
            Parameter.of("to", Type.ADDRESS, to),
            Parameter.of("value", Type.UINT256, value),
        )

        val hash = evmService.transaction(
            "transfer",
            contractAddress,
            transactionOptions,
            parameters
        )

        return evmService.waitForTransaction(hash, transactionOptions)
    }

    /**
     * Transfers a specified amount of ERC20 tokens from an account (from) to another account (to)
     * using allowance from the `spender`.
     *
     * @param from The address of the source account
     * @param to The address of the recipient account
     * @param value The amount of tokens to transfer
     * @param spender The address of the account that approved spending on behalf of `from`
     * @return The transaction receipt for the transfer operation
     */
    @Suspendable
    fun transferFrom(from: String, to: String, value: BigInteger): TransactionReceipt {
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

        // REVIEW: cannot set transaction options inside the contract functions?

        val parameters = listOf(
            Parameter.of("from", Type.ADDRESS, from),
            Parameter.of("to", Type.ADDRESS, to),
            Parameter.of("value", Type.UINT256, value),
        )

        val hash = evmService.transaction(
            "transferFrom",
            contractAddress,
            transactionOptions,
            parameters
        )

        return evmService.waitForTransaction(hash, transactionOptions)

    }

    /**
     * Approves a third-party spender (spender) to transfer a certain amount of ERC20 tokens on behalf of the calling account.
     *
     * @param spender The address of the third-party spender
     * @param value The maximum amount of tokens that the spender can transfer on behalf of the calling account
     * @return The transaction receipt for the approval operation
     */
    @Suspendable
    fun approve(spender: String, value: BigInteger): TransactionReceipt {
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
            Parameter.of("spender", Type.ADDRESS, spender),
            Parameter.of("value", Type.UINT256, value),
        )

        val hash = evmService.transaction(
            "approve",
            contractAddress,
            transactionOptions,
            parameters
        )

        return evmService.waitForTransaction(hash, transactionOptions)
    }

    /**
     * Gets the current allowance for a particular spender to transfer ERC20 tokens on behalf of the calling account.
     *
     * @param spender The address of the third-party spender
     * @return The maximum amount of tokens that the spender can transfer on behalf of the calling account
     */
    @Suspendable
    fun allowance(owner: String, spender: String): BigInteger {
        return evmService.call(
            "allowance",
            contractAddress,
            CallOptions(
                EvmOptions(
                    rpcUrl,
                    ""
                )
            ),
            Type.UINT256,
            Parameter.of("owner", Type.ADDRESS, owner),
            Parameter.of("spender", Type.ADDRESS, spender),
        )
    }

}
