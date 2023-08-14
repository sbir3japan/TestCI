package net.corda.interop.web3j.internal

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlin.reflect.KClass
import com.google.gson.JsonParser
import net.corda.v5.base.exceptions.CordaRuntimeException


data class JsonRpcResponse @JsonCreator constructor(
    @JsonProperty("jsonrpc") val jsonrpc: String,
    @JsonProperty("id") val id: String,
    @JsonProperty("result") val result: String?
)

class EVMErrorException(val errorResponse: JsonRpcError) : Exception("Custom error")

data class JsonRpcError @JsonCreator constructor(
    @JsonProperty("jsonrpc") val jsonrpc: String,
    @JsonProperty("id") val id: String,
    @JsonProperty("error") val error: Error
)

data class Error @JsonCreator constructor(
    @JsonProperty("code") val code: Int,
    @JsonProperty("message") val message: String,
    @JsonProperty("data") val data: String?
)

data class RpcRequest(
    val jsonrpc: String,
    val id: String,
    val method: String,
    val params: List<*>
)


data class ProcessedResponse(
    val success: Boolean,
    val payload: String?
)

data class RPCResponse (
    val success:Boolean,
    val message: String
)



//@JsonCreator
//data class Response (
//    val id: String,
//    val jsonrpc: String,
//    val result: Any?,
//)

data class Response @JsonCreator constructor(
    @JsonProperty("id") val id: String,
    @JsonProperty("jsonrpc") val jsonrpc: String,
    @JsonProperty("result") val result: Any?

)

data class TransactionResponse @JsonCreator constructor(
    @JsonProperty("id") val id: String,
    @JsonProperty("jsonrpc") val jsonrpc: String,
    @JsonProperty("result") val result: TransactionData
)

data class TransactionData @JsonCreator constructor(
    @JsonProperty("blockHash") val blockHash: String,
    @JsonProperty("blockNumber") val blockNumber: String,
    @JsonProperty("contractAddress") val contractAddress: String,
    @JsonProperty("cumulativeGasUsed") val cumulativeGasUsed: String,
    @JsonProperty("from") val from: String,
    @JsonProperty("gasUsed") val gasUsed: String,
    @JsonProperty("effectiveGasPrice") val effectiveGasPrice: String,
    @JsonProperty("logs") val logs: List<TransactionLog>,
    @JsonProperty("logsBloom") val logsBloom: String,
    @JsonProperty("status") val status: String,
    @JsonProperty("to") val to: String?,
    @JsonProperty("transactionHash") val transactionHash: String,
    @JsonProperty("transactionIndex") val transactionIndex: String,
    @JsonProperty("type") val type: String
)

data class TransactionLog @JsonCreator constructor(
    @JsonProperty("address") val address: String,
    @JsonProperty("topics") val topics: List<String>,
    @JsonProperty("data") val data: String,
    @JsonProperty("blockNumber") val blockNumber: String,
    @JsonProperty("transactionHash") val transactionHash: String,
    @JsonProperty("transactionIndex") val transactionIndex: String,
    @JsonProperty("blockHash") val blockHash: String,
    @JsonProperty("logIndex") val logIndex: String,
    @JsonProperty("removed") val removed: Boolean
)

class EthereumConnector {

    companion object {
        private const val JSON_RPC_VERSION = "2.0"
    }

    private val objectMapper = ObjectMapper()

    private val maxLoopedRequests = 10

    private fun checkNestedKey(jsonObject: JsonObject, nestedKey: String): Boolean {
        if (jsonObject.has(nestedKey)) {
            return true
        }

        for ((_, value) in jsonObject.entrySet()) {
            if (value.isJsonObject) {
                if (checkNestedKey(value.asJsonObject, nestedKey)) {
                    return true
                }
            }
        }

        return false
    }

    private fun jsonStringContainsNestedKey(jsonString: String, nestedKey: String): Boolean {
        return try {
            val jsonObject = JsonParser().parse(jsonString).asJsonObject
            checkNestedKey(jsonObject, nestedKey)
        } catch (e: Exception) {
            // Handle any parsing errors here
            false
        }
    }


    private fun jsonStringContainsKey(jsonString: String, key: String): Boolean {
        return try {
            val jsonObject = JsonParser().parse(jsonString).asJsonObject
            jsonObject.has(key)
        } catch (e: Exception) {
            // Handle any parsing errors here
            false
        }
    }
    /**
     * Finds the appropriate data class from the candidateDataClasses list that fits the JSON structure.
     *
     * @param json The JSON string to be parsed.
     * @return The matching data class from candidateDataClasses, or null if no match is found.
     */
    private fun findDataClassForJson(json: String): KClass<*>? {
        if (jsonStringContainsKey(json, "error")) {
            return JsonRpcError::class
        } else if (jsonStringContainsNestedKey(json, "contractAddress")) {
            return TransactionResponse::class
        } else {
            return JsonRpcResponse::class
        }

    }

    /**
     * Returns the useful data from the given input based on its type.
     *
     * @param input The input data object to process.
     * @return The useful data extracted from the input as a string, or an empty string if not applicable.
     */
    private fun returnUsefulData(input: Any): ProcessedResponse {
        println("INPUT ${input}")
        when (input) {
            is JsonRpcError -> {
                 throw EVMErrorException(input)
            }
            is TransactionResponse -> {
                try{
                    return ProcessedResponse(true, input.result.contractAddress)
                }catch(e: Exception){
                    return ProcessedResponse(true, input.result.toString())
                }                }
            is JsonRpcResponse -> return ProcessedResponse(true,input.result)
        }
        return ProcessedResponse(false,"")
    }



    /**
     * Makes an RPC call to the Ethereum node and returns the JSON response as an RPCResponse object.
     *
     * @param rpcUrl The URL of the Ethereum RPC endpoint.
     * @param method The RPC method to call.
     * @param params The parameters for the RPC call.
     * @return An RPCResponse object representing the result of the RPC call.
     */
    private fun rpcCall(rpcUrl: String, method: String, params: List<Any?>): RPCResponse {
        val body = RpcRequest(JSON_RPC_VERSION, "90.0", method, params)
        val requestBase = objectMapper.writeValueAsString(body)
        val requestBody = requestBase.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(rpcUrl)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()
        return OkHttpClient().newCall(request).execute().body?.use {
            RPCResponse(true, it.string())
        } ?: throw CordaRuntimeException("Response was null")
    }
    /**
     * Makes an RPC request to the Ethereum node and waits for the response.
     *
     * @param rpcUrl The URL of the Ethereum RPC endpoint.
     * @param method The RPC method to call.
     * @param params The parameters for the RPC call.
     * @param waitForResponse Set to true if the function should wait for a response, otherwise false.
     * @param requests The number of requests made so far (used for recursive calls).
     * @return A Response object representing the result of the RPC call.
     */
    private fun makeRequest(
        rpcUrl: String,
        method: String,
        params: List<*>,
        waitForResponse: Boolean,
        requests: Int
    ): Response {
        println(waitForResponse)
        println("PARAMS ${params}")
            // Check if the maximum number of requests has been reached
            if (requests > maxLoopedRequests) {
                return Response("90", "2.0", "Timed Out")
            }

            // Make the RPC call to the Ethereum node
            val response = rpcCall(rpcUrl, method, params)
            val responseBody = response.message
            val success = response.success

            // Handle the response based on success status
            if (!success) {
                println("Request Failed")
                return Response("90", "2.0", response.message)
            }

            // Parse the JSON response into the base response object
            println("Response Body: $responseBody ")

        // If the base response is null and waitForResponse is true, wait for 2 seconds and make a recursive call
            // TODO: This is temporarily required for



            // Find the appropriate data class for parsing the actual response
            val responseType = findDataClassForJson(
                responseBody
            )

            println("RESPONSE BODY: ${responseBody}")
            // Parse the actual response using the determined data class
            val actualParsedResponse = objectMapper.readValue(responseBody, responseType?.java ?: Any::class.java)
            // Get the useful response data from the parsed response


            val usefulResponse = returnUsefulData(actualParsedResponse)
        // simplify this
        if (usefulResponse.payload == null || usefulResponse.payload=="null" && waitForResponse) {
            TimeUnit.SECONDS.sleep(2)
            return makeRequest(rpcUrl, method, params, true, requests + 1) // Return the recursive call
        }
            return Response("90", "2.0", usefulResponse.payload)
    }

    /**
     * Sends an RPC request to the Ethereum node and returns the response without waiting for it.
     *
     * @param rpcUrl The URL of the Ethereum RPC endpoint.
     * @param method The RPC method to call.
     * @param params The parameters for the RPC call.
     * @return A Response object representing the result of the RPC call.
     */
    fun send(rpcUrl: String, method: String, params: List<*>): Response {
        return makeRequest(rpcUrl, method, params, waitForResponse = false, requests = 0)
    }

    /**
     * Sends an RPC request to the Ethereum node and returns the response.
     *
     * @param rpcUrl The URL of the Ethereum RPC endpoint.
     * @param method The RPC method to call.
     * @param params The parameters for the RPC call.
     * @param waitForResponse Set to true if the function should wait for a response, otherwise false.
     * @return A Response object representing the result of the RPC call.
     */
    fun send(rpcUrl: String, method: String, params: List<*>, waitForResponse: Boolean): Response {
        return makeRequest(rpcUrl, method, params, waitForResponse, requests = 0)
    }

}