package net.corda.interop.evm

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.exceptions.CordaRuntimeException

class EVMErrorException(val errorResponse: JsonRpcError) : CordaRuntimeException(errorResponse.error.toString())

data class JsonRpcError @JsonCreator constructor(
    @JsonProperty("jsonrpc") val jsonrpc: String,
    @JsonProperty("id") val id: String,
    @JsonProperty("error") val error: Error,
) : EvmResponse

data class Error @JsonCreator constructor(
    @JsonProperty("code") val code: Int,
    @JsonProperty("message") val message: String,
    @JsonProperty("data") val data: String?,
)

data class RpcRequest @JsonCreator constructor(
    @JsonProperty("jsonrpc") val jsonrpc: String,
    @JsonProperty("id") val id: String,
    @JsonProperty("method") val method: String,
    @JsonProperty("params") val params: List<*>,
)


interface EvmResponse

data class GenericResponse @JsonCreator constructor(
    @JsonProperty("id") val id: String,
    @JsonProperty("jsonrpc") val jsonrpc: String,
    @JsonProperty("result") val result: String?,
)

data class Response @JsonCreator constructor(
    @JsonProperty("id") val id: String,
    @JsonProperty("jsonrpc") val jsonrpc: String,
    @JsonProperty("result") val result: TransactionData,
)

data class TransactionData @JsonCreator constructor(
    @JsonProperty("blockHash") val blockHash: String,
    @JsonProperty("blockNumber") val blockNumber: String,
    @JsonProperty("contractAddress") val contractAddress: String?,
    @JsonProperty("cumulativeGasUsed") val cumulativeGasUsed: String,
    @JsonProperty("from") val from: String,
    @JsonProperty("gasUsed") val gasUsed: String,
    @JsonProperty("effectiveGasPrice") val effectiveGasPrice: String?,
    @JsonProperty("logs") val logs: List<TransactionLog>,
    @JsonProperty("logsBloom") val logsBloom: String,
    @JsonProperty("status") val status: String,
    @JsonProperty("to") val to: String?,
    @JsonProperty("transactionHash") val transactionHash: String,
    @JsonProperty("transactionIndex") val transactionIndex: String,
    @JsonProperty("extDataGasUsed") val extDataGasUsed: String?,
    @JsonProperty("type") val type: String?,
) : EvmResponse

data class TransactionLog @JsonCreator constructor(
    @JsonProperty("address") val address: String,
    @JsonProperty("topics") val topics: List<String>,
    @JsonProperty("data") val data: String,
    @JsonProperty("blockNumber") val blockNumber: String,
    @JsonProperty("transactionHash") val transactionHash: String,
    @JsonProperty("transactionIndex") val transactionIndex: String,
    @JsonProperty("blockHash") val blockHash: String,
    @JsonProperty("logIndex") val logIndex: String?,
    @JsonProperty("removed") val removed: Boolean,
    @JsonProperty("extDataGasUsed") val extDataGasUsed: String?,
    @JsonProperty("type") val type: String?,
)




@CordaSerializable
data class EthereumBlock @JsonCreator constructor(
    @JsonProperty("id")
    val id: String,

    @JsonProperty("jsonrpc")
    val jsonrpc: String,

    @JsonProperty("result")
    val result: EthereumBlockResult
)

@CordaSerializable
data class EthereumBlockResult @JsonCreator constructor(
    @JsonProperty("number")
    val number: String,

    @JsonProperty("hash")
    val hash: String,

    @JsonProperty("parentHash")
    val parentHash: String,

    @JsonProperty("mixHash")
    val mixHash: String,

    @JsonProperty("nonce")
    val nonce: String,

    @JsonProperty("sha3Uncles")
    val sha3Uncles: String,

    @JsonProperty("logsBloom")
    val logsBloom: String,

    @JsonProperty("transactionsRoot")
    val transactionsRoot: String,

    @JsonProperty("stateRoot")
    val stateRoot: String,

    @JsonProperty("receiptsRoot")
    val receiptsRoot: String,

    @JsonProperty("miner")
    val miner: String,

    @JsonProperty("difficulty")
    val difficulty: String,

    @JsonProperty("totalDifficulty")
    val totalDifficulty: String,

    @JsonProperty("extraData")
    val extraData: String,

    @JsonProperty("size")
    val size: String,

    @JsonProperty("gasLimit")
    val gasLimit: String,

    @JsonProperty("gasUsed")
    val gasUsed: String,

    @JsonProperty("timestamp")
    val timestamp: String,

    @JsonProperty("transactions")
    val transactions: List<String>,

    @JsonProperty("uncles")
    val uncles: List<String>
)

@CordaSerializable
data class EthereumTransaction @JsonCreator constructor(
    @JsonProperty("hash")
    val hash: String,

    @JsonProperty("nonce")
    val nonce: String,

    @JsonProperty("blockHash")
    val blockHash: String,

    @JsonProperty("blockNumber")
    val blockNumber: String,

    @JsonProperty("transactionIndex")
    val transactionIndex: String,

    @JsonProperty("from")
    val from: String,

    @JsonProperty("to")
    val to: String?,

    @JsonProperty("value")
    val value: String,

    @JsonProperty("gas")
    val gas: String,

    @JsonProperty("gasPrice")
    val gasPrice: String,

    @JsonProperty("input")
    val input: String,

    @JsonProperty("v")
    val v: String,

    @JsonProperty("r")
    val r: String,

    @JsonProperty("s")
    val s: String


)



data class TransactionResponse @JsonCreator constructor(
    @JsonProperty("id") val id: String,
    @JsonProperty("jsonrpc") val jsonrpc: String,
    @JsonProperty("result") val result: TransactionObjectData,
)

data class TransactionObjectData @JsonCreator constructor(
    @JsonProperty("blockHash") val blockHash: String,
    @JsonProperty("blockNumber") val blockNumber: String,
    @JsonProperty("from") val from: String,
    @JsonProperty("gas") val gas: String?,
    @JsonProperty("gasPrice") val gasPrice: String?,
    @JsonProperty("maxFeePerGas") val maxFeePerGas: String?,
    @JsonProperty("maxPriorityFeePerGas") val maxPriorityFeePerGas: String?,
    @JsonProperty("hash") val hash: String,
    @JsonProperty("input") val input: String,
    @JsonProperty("nonce") val nonce: String,
    @JsonProperty("to") val to: String?,
    @JsonProperty("transactionIndex") val transactionIndex: String,
    @JsonProperty("value") val value: String,
    @JsonProperty("type") val type: String?,
    @JsonProperty("v") val v: String,
    @JsonProperty("r") val r: String,
    @JsonProperty("s") val s: String,
    )

