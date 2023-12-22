package com.r3.corda.demo.swaps.workflows

import java.math.BigInteger
import net.corda.v5.application.interop.evm.EvmService
import net.corda.v5.application.interop.evm.Parameter
import net.corda.v5.application.interop.evm.TransactionReceipt
import net.corda.v5.application.interop.evm.Type
import net.corda.v5.application.interop.evm.options.CallOptions
import net.corda.v5.application.interop.evm.options.EvmOptions
import net.corda.v5.application.interop.evm.options.TransactionOptions

class ERC20(
    private val rpcUrl: String,
    private val evmService: EvmService,
    private val contractAddress: String,
) {

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

    fun transfer(to: String, value: BigInteger): TransactionReceipt {
        val dummyGasNumber = BigInteger("a41c5", 16)
        val transactionOptions = TransactionOptions(
            dummyGasNumber,                 // gasLimit
            0.toBigInteger(),               // value
            20000000000.toBigInteger(),     // maxFeePerGas
            20000000000.toBigInteger(),     // maxPriorityFeePerGas
            rpcUrl,                // rpcUrl
            contractAddress,          // from
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

    fun transferFrom(from: String, to: String, value: BigInteger): TransactionReceipt {
        val dummyGasNumber = BigInteger("a41c5", 16)
        val transactionOptions = TransactionOptions(
            dummyGasNumber,                 // gasLimit
            0.toBigInteger(),               // value
            20000000000.toBigInteger(),     // maxFeePerGas
            20000000000.toBigInteger(),     // maxPriorityFeePerGas
            rpcUrl,                // rpcUrl
            contractAddress,          // from
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

    fun approve(spender: String, value: BigInteger): TransactionReceipt {
        val dummyGasNumber = BigInteger("a41c5", 16)
        val transactionOptions = TransactionOptions(
            dummyGasNumber,                 // gasLimit
            0.toBigInteger(),               // value
            20000000000.toBigInteger(),     // maxFeePerGas
            20000000000.toBigInteger(),     // maxPriorityFeePerGas
            rpcUrl,                // rpcUrl
            contractAddress,          // from
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
