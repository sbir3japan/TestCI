package com.r3.corda.demo.swaps.states.swap

import com.r3.corda.demo.swaps.utils.trie.SimpleKeyValueStore
import net.corda.v5.application.interop.evm.Log
import net.corda.v5.application.interop.evm.TransactionReceipt
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.DigitalSignature
import java.math.BigInteger


@CordaSerializable
data class UnlockData(
    val merkleProof: SimpleKeyValueStore,
    val validatorSignatures: List<DigitalSignature.WithKeyId>,
    val receiptsRootHash: String,
    val transactionReceipt: SerializableTransactionReceipt
)

@CordaSerializable
data class SerializableTransactionReceipt(
    val blockHash: String,
    val blockNumber: BigInteger,
    val contractAddress: String?,
    val cumulativeGasUsed: BigInteger,
    val effectiveGasPrice: BigInteger?,
    val from: String,
    val gasUsed: BigInteger,
    val logs: List<SerializableLog>,
    val logsBloom: String,
    val status: Boolean,
    val to: String?,
    val transactionHash: String,
    val transactionIndex: BigInteger,
    val type: String
) {
    companion object {
        fun fromTransactionReceipt(transactionReceipt: TransactionReceipt): SerializableTransactionReceipt {
            return SerializableTransactionReceipt(
                blockHash = transactionReceipt.blockHash,
                blockNumber = transactionReceipt.blockNumber,
                contractAddress = transactionReceipt.contractAddress,
                cumulativeGasUsed = transactionReceipt.cumulativeGasUsed,
                effectiveGasPrice = transactionReceipt.effectiveGasPrice,
                from = transactionReceipt.from,
                gasUsed = transactionReceipt.gasUsed,
                logs = transactionReceipt.logs.map { SerializableLog.fromLog(it) },
                logsBloom = transactionReceipt.logsBloom,
                status = transactionReceipt.status,
                to = transactionReceipt.to,
                transactionHash = transactionReceipt.transactionHash,
                transactionIndex = transactionReceipt.transactionIndex,
                type = transactionReceipt.type
            )
        }
    }

    fun toTransactionReceipt(): TransactionReceipt {
        return TransactionReceipt(
            this.blockHash,
            this.blockNumber,
            this.contractAddress,
            this.cumulativeGasUsed,
            this.effectiveGasPrice,
            this.from,
            this.gasUsed,
            this.logs.map { it.toLog() },
            this.logsBloom,
            this.status,
            this.to,
            this.transactionHash,
            this.transactionIndex,
            this.type
        )
    }
}

@CordaSerializable
data class SerializableLog(
    val address: String,
    val topics: List<String>,
    val data: String,
    val blockNumber: BigInteger,
    val transactionHash: String?,
    val transactionIndex: BigInteger?,
    val blockHash: String,
    val logIndex: Int,
    val removed: Boolean
) {
    companion object {
        fun fromLog(log: Log): SerializableLog {
            return SerializableLog(
                address = log.address,
                topics = log.topics,
                data = log.data,
                blockNumber = log.blockNumber,
                transactionHash = log.transactionHash,
                transactionIndex = log.transactionIndex,
                blockHash = log.blockHash,
                logIndex = log.logIndex,
                removed = false // log.removed // TODO: review removed inaccessible
            )
        }
    }

    fun toLog(): Log {
        return Log(
            address,
            topics,
            data,
            blockNumber,
            transactionHash,
            transactionIndex,
            blockHash,
            logIndex,
            removed
        )
    }
}
