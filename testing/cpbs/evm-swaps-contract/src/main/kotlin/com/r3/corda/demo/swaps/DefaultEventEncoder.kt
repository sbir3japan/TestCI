package com.r3.corda.demo.swaps

import com.r3.corda.demo.swaps.states.swap.SerializableTransactionReceipt
import net.corda.v5.application.interop.evm.TransactionReceipt
import net.corda.v5.base.annotations.CordaSerializable
import org.abi.TypeEncoder
import org.abi.datatypes.*
import org.abi.datatypes.generated.Bytes32
import org.abi.datatypes.generated.Int256
import org.abi.datatypes.generated.Uint256
import org.abi.datatypes.generated.Uint8
import org.crypto.Hash
import org.utils.Numeric
import java.math.BigInteger

/**
 * The [Indexed] class allows to decorate an event argument with the indexed attribute
 */
data class Indexed<T>(val indexedValue: T)

object DefaultEventEncoder {

    private val whitespaceRegex = Regex("\\s+")

    /**
     * Encodes an EVM event based on its contract address, event signature, and event parameters.
     *
     * Example:
     * given the Solidity event `event Transfer(address indexed sender, address indexedreceiver, uint256 amount)`, the
     * event signature will be  `Transfer(address,address,uint256` and the params Indexed(sender), Indexed(recipient),
     * amount.
     *
     * @return An [EncodedEvent] that can allows to search [TransactionReceiptLog]s for a matching event from the
     *         expected address.
     */
    fun encodeEvent(contractAddress: String, eventSignature: String, vararg params: Any): EncodedEvent {
        val paramTypes = eventSignature.substringAfter('(').substringBefore(')').split(",")

        val typesWithValues = params.zip(paramTypes).map { (value, typeString) ->
            val isIndexed = value is Indexed<*>
            val actualValue = if (isIndexed) (value as Indexed<*>).indexedValue else value

            val type = when (typeString.trim()) {
                "string" -> Utf8String(actualValue as String)
                "uint256" -> Uint256(actualValue as BigInteger)
                "uint8" -> Uint8(actualValue as BigInteger)
                "int256" -> Int256(actualValue as BigInteger)
                "address" -> Address(actualValue as String)
                "bool" -> Bool(actualValue as Boolean)
                "bytes" -> DynamicBytes(actualValue as ByteArray)
                "bytes32" -> actualValue as Bytes32//StaticBytes32(actualValue as ByteArray)
                else -> throw IllegalArgumentException("Unsupported type: $typeString")
            }

            Triple(type, isIndexed, typeString.trim())
        }

        val topic0 = Hash.sha3String(eventSignature)
        val topics = mutableListOf(topic0)

        typesWithValues.filter { it.second }.forEach { (type, _, typeString) ->
            val topic = when {
                typeString == "string" || typeString == "bytes" -> if (typeString == "string") Hash.sha3String(type.toString()) else Hash.sha3(
                    TypeEncoder.encode(type)
                )

                type is Address -> Numeric.toHexStringWithPrefixZeroPadded(
                    Numeric.toBigInt(type.value),
                    64
                ) // Ensures 32 bytes length with 0x prefix
                type is BytesType -> Numeric.toHexStringWithPrefixZeroPadded(BigInteger(type.value), 64)
                type is NumericType -> Numeric.toHexStringWithPrefixZeroPadded(type.value as BigInteger, 64)
                else -> throw IllegalArgumentException("Unsupported indexed type: $typeString")
            }
            topics.add(topic)
        }

        val data = typesWithValues.filterNot { it.second }
            .joinToString("") { TypeEncoder.encode(it.first) }

        return EncodedEvent(contractAddress, topics, Numeric.prependHexPrefix(data))
    }
}

/**
 * Stores an event's contract address, topics, and data. These are the predictable properties of an EVM event and as
 * such they can be used to look-up matching events in a set of transaction logs like in the case of a transaction that
 * emits multiple events or in the case of a block that contains multiple transaction receipts with multiple transaction
 * logs.
 */
@CordaSerializable
data class EncodedEvent(
    val address: String,
    val topics: List<String>,
    val data: String
) {
    companion object {
        val defaultLog = net.corda.v5.application.interop.evm.Log(
            "", emptyList(),
            "",
            BigInteger.ZERO,
            "",
            BigInteger.ZERO,
            "",
            0,
            false
        );
    }

    data class Log(val found: Boolean, val log: net.corda.v5.application.interop.evm.Log)

    /**
     * Check whether this [EncodedEvent] matches any log entry in the transaction receipt logs.
     * @param receipt The [receipt] that contains the logs to look up into.
     * @return True if any log matches this [EncodedEvent] instance.
     */
    fun isFoundIn(receipt: SerializableTransactionReceipt): Boolean {
    //fun isFoundIn(receipt: TransactionReceipt): Boolean {
        // NOTE: while generally speaking there may be multiple events with the same parameters, our use cases expects
        //       it to be unique due to the presence of Draft Transaction ID and the rules of the contract that does not
        //       allow its reuse
        return receipt.status && receipt.logs.count {
            // !it.isRemoved && // TODO: Add isRemoved to transaction receipt it is fundamental as it tells if the event was added or removed and should always be added
                    address.equals(it.address, ignoreCase = true) &&
                    areTopicsEqual(it.topics, topics) &&
                    data.equals(it.data, ignoreCase = true)
        } == 1
    }

    /**
     * Check whether this [EncodedEvent] matches any log entry in the transaction receipt logs.
     * @param receipt The [receipt] that contains the logs to look up into.
     * @return Return the log entry if found in the logs.
     */
    fun findIn(receipt: TransactionReceipt): Log {
        var log: net.corda.v5.application.interop.evm.Log? = null

        if (receipt.status) {
            log = receipt.logs.singleOrNull {
                !it.isRemoved &&
                        address.equals(it.address, ignoreCase = true) &&
                        areTopicsEqual(it.topics, topics) &&
                        data.equals(it.data, ignoreCase = true)
            }
        }

        return Log(log != null, log ?: defaultLog)
    }

    /**
     * Compares two topic lists for contents equality.
     */
    private fun areTopicsEqual(topics: List<String>?, other: List<String>): Boolean {
        if (topics == null || topics.size != other.size) return false

        for (i in topics.indices) {
            if (!other[i].equals(topics[i], ignoreCase = true)) {
                return false
            }
        }

        return true
    }
}

data class UnencodedEvent(
    val data: List<Type<*>>
) {
    constructor(vararg args: Type<*>) : this(args.toList()) {
    }
}
