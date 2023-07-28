package net.corda.processors.evm.internal

import com.google.gson.JsonObject
import net.corda.data.crypto.wire.CryptoResponseContext
import net.corda.data.crypto.wire.ops.rpc.RpcOpsRequest
import net.corda.data.interop.evm.EvmRequest
import net.corda.data.interop.evm.EvmResponse
import net.corda.messaging.api.processor.RPCResponderProcessor
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.CompletableFuture
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.service.TxSignServiceImpl
import org.web3j.utils.Numeric
import java.math.BigInteger

class EVMOpsProcessor() : RPCResponderProcessor<EvmRequest, EvmResponse> {
    val evmConnector = EthereumConnector()
    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)



//        const val CLIENT_ID_REST_PROCESSOR = "rest.processor"
    }


    private fun getTransactionCount(rpcUrl: String, address: String): BigInteger {
        val transactionCountResponse = evmConnector.send(
            rpcUrl,
            "eth_getTransactionCount",
            listOf(address, "latest")
        );
        return BigInteger.valueOf(Integer.decode(transactionCountResponse.result.toString()).toLong())
    }


    private fun sendTransaction(rpcConnection: String, contractAddress: String, payload: String): String{
        val nonce = getTransactionCount(rpcConnection, "0xbD820E71b2D7E09DE2391E9aBd395d5e9D9630bb")
        log.info("Nonce ${nonce.toString()}")
        val transaction = RawTransaction.createTransaction(
            1337.toLong(),
            nonce,
            BigInteger.valueOf(10000000),
            contractAddress,
            BigInteger.valueOf(0),
            payload,
            BigInteger.valueOf(10000000),
            BigInteger.valueOf(51581475500)
        )

        val signer = Credentials.create("0x1756c2706ac58e2a9a4cb8b65555a79c292e97ea619e393cf7d38cbf9e2d6231")
        val signed = TxSignServiceImpl(signer).sign(transaction,"1337".toLong())
        return EthereumConnector().send(rpcConnection, "eth_sendRawTransaction",listOf(Numeric.toHexString(signed))).result.toString()
    }


    private fun sendCall(rpcConnection: String, contractAddress: String, payload: String): String{
        val rootObject= JsonObject()
        rootObject.addProperty("to",contractAddress)
        rootObject.addProperty("data",payload)
        rootObject.addProperty("input",payload)
        val resp = evmConnector.send(rpcConnection,"eth_call", listOf(rootObject,"latest"))
        return resp.result.toString()
    }




    override fun onNext(request: EvmRequest, respFuture: CompletableFuture<EvmResponse>) {
        log.info(request.schema.toString(true))

        val contractAddress = request.contractAddress
        val rpcConnection = request.rpcUrl
        val payload = request.payload
        val flowId = request.flowId
        val isTransaction = request.isTransaction



        try {
            if (isTransaction) {
                val transactionOutput = sendTransaction(rpcConnection, contractAddress, payload)
                val result = EvmResponse(flowId,transactionOutput)
                respFuture.complete(result)

            } else {
                // TODO: Abstract to query Call
                val callResult =  sendCall(rpcConnection,contractAddress,payload)
                respFuture.complete(EvmResponse(flowId,callResult))
            }
        }catch (e: Throwable){
            respFuture.completeExceptionally(e)
        }

    }


    // TODO: Identifiy the EVMResponseContext we need here
    private fun createResponseContext(request: RpcOpsRequest) = CryptoResponseContext(
        request.context.requestingComponent,
        request.context.requestTimestamp,
        request.context.requestId,
        Instant.now(),
        request.context.tenantId,
        request.context.other
    )


}
