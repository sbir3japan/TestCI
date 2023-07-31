package net.corda.processors.evm.internal

import com.google.gson.JsonObject
import net.corda.data.interop.evm.EvmRequest
import net.corda.data.interop.evm.EvmResponse
import net.corda.messaging.api.processor.RPCResponderProcessor
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import org.web3j.crypto.Credentials
import org.web3j.service.TxSignServiceImpl
import org.web3j.crypto.RawTransaction
import org.web3j.utils.Numeric
import java.math.BigInteger



// This is a processor that will send transaction & calls to the respective EVM Network and
// Process back the return message.
class EVMOpsProcessor() : RPCResponderProcessor<EvmRequest, EvmResponse> {
    val evmConnector = EthereumConnector()
    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    // Get the transaction receipt details
    private fun getTransactionReceipt(rpcConnection: String, receipt: String): String {
        val resp = evmConnector.send(rpcConnection,"eth_getTransactionReceipt",listOf(receipt))
        return resp.result.toString()
    }

    // Get the set Gas Price
    private fun getGasPrice(rpcConnection: String,): BigInteger {
        val resp = evmConnector.send(rpcConnection,"eth_gasPrice", listOf(""))
        return BigInteger.valueOf(Integer.decode(resp.result.toString()).toLong())
    }

    // Estimate the Gas price
    private fun estimateGas(rpcConnection: String, from: String, to: String, payload: String): BigInteger {
        val rootObject= JsonObject()
        rootObject.addProperty("to",to)
        rootObject.addProperty("data",payload)
        rootObject.addProperty("input",payload)
        rootObject.addProperty("from",from)
        val resp = evmConnector.send(rpcConnection,"eth_estimateGas", listOf(rootObject,"latest"))
        return BigInteger.valueOf(Integer.decode(resp.result.toString()).toLong())
    }

    // Fetches the amount of transactions an address has made
    private fun getTransactionCount(rpcUrl: String, address: String): BigInteger {
        val transactionCountResponse = evmConnector.send(
            rpcUrl,
            "eth_getTransactionCount",
            listOf(address, "latest")
        );
        return BigInteger.valueOf(Integer.decode(transactionCountResponse.result.toString()).toLong())
    }

    // Sends off a transaction
    private fun sendTransaction(rpcConnection: String, contractAddress: String, payload: String): String{
        val nonce = getTransactionCount(rpcConnection, "0xbD820E71b2D7E09DE2391E9aBd395d5e9D9630bb")
        val estimatedGas = estimateGas(rpcConnection,"0xbD820E71b2D7E09DE2391E9aBd395d5e9D9630bb", contractAddress, payload)

        // Usefor for pre EIP-1559 ones
        println("Estimated Gas: ${estimatedGas}")

        // TODO:  Allow for gas fee estimation
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
        val tReceipt = evmConnector.send(rpcConnection, "eth_sendRawTransaction",listOf(Numeric.toHexString(signed))).result.toString()
        println("Receipt: ${tReceipt}")
        val transactionOutput = getTransactionReceipt(rpcConnection,tReceipt)
        println("Transaction Details: ${transactionOutput}")
        return tReceipt
    }


    // Make a smart contract call
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
        // Paramaters for the transaction/queryS
        val contractAddress = request.contractAddress
        val rpcConnection = request.rpcUrl
        val payload = request.payload
        val flowId = request.flowId
        val isTransaction = request.isTransaction

        try {
            if (isTransaction) {
                // Transaction Being Sent
                val transactionOutput = sendTransaction(rpcConnection, contractAddress, payload)
                val result = EvmResponse(flowId,transactionOutput)
                respFuture.complete(result)

            } else {
                // Call Being Sent
                val callResult =  sendCall(rpcConnection,contractAddress,payload)
                respFuture.complete(EvmResponse(flowId,callResult))
            }
        }catch (e: Throwable){
            // On Error Return the Error
            // Better error handling => Meaningful
            //
            respFuture.completeExceptionally(e)
        }
    }
}
