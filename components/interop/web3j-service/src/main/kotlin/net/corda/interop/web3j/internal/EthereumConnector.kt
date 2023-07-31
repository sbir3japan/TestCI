package net.corda.interop.web3j.internal

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.Gson


data class RpcRequest(
    val jsonrpc: String,
    val id: String,
    val method: String,
    val params: List<*>
)



data class Response (
    val id: String,
    val jsonrpc: String,
    val result: Any,
)

class EthereumConnector {
    fun send(rpcUrl: String, method: String, params: List<*>): Response {
        try {
            val gson = Gson()
            val client = OkHttpClient()
            val body = RpcRequest(
                jsonrpc = "2.0",
                id = "90.0",
                method = method,
                params = params
            )
            val requestBase = gson.toJson(body)
            val requestBody = requestBase.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(rpcUrl)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            val parsedResponse = gson.fromJson(responseBody, Response::class.java)
            response.close()
            return parsedResponse

        } catch (e: Exception) {
            e.printStackTrace()
            // TODO: Fix error handling
            return Response("", "", e.message.toString())

        }
    }
}
