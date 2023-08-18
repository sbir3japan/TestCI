package net.corda.interop.web3j.internal.besu

import net.corda.data.interop.evm.EvmRequest
import net.corda.data.interop.evm.EvmResponse
import net.corda.data.interop.evm.request.SendRawTransaction
import net.corda.interop.web3j.EvmDispatcher
import net.corda.interop.web3j.internal.EthereumConnector
import net.corda.interop.web3j.internal.NonEip1559Block
import net.corda.interop.web3j.internal.NonEip1559BlockData
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.service.TxSignServiceImpl
import org.web3j.utils.Numeric
import java.math.BigInteger

class BesuSendRawTransactionDispatcher(val evmConnector: EthereumConnector) : EvmDispatcher {


    /**
     * Query the completion status of a contract using the Ethereum node.
     *
     * @param rpcConnection The URL of the Ethereum RPC endpoint.
     * @param transactionHash The hash of the transaction to query.
     * @return The JSON representation of the transaction receipt.
     */
    private fun queryCompletionContract(rpcConnection: String, transactionHash: String): String {
        val resp = evmConnector.send(rpcConnection, "eth_getTransactionReceipt", listOf(transactionHash), true)
        return resp.result.toString()
    }


    /**
     * Retrieves the balance of a given Ethereum address using the provided RPC connection.
     *
     * @param rpcConnection The RPC connection URL for Ethereum communication.
     * @param from The Ethereum address for which to retrieve the balance.
     * @return The balance of the specified Ethereum address as a string.
     */
    override fun dispatch(evmRequest: EvmRequest): EvmResponse {
        // Send an RPC request to retrieve the maximum priority fee per gas.
        println("BEFORE PREP")
        val sentTransaction = evmRequest.payload as SendRawTransaction
        val transactionCountResponse = evmConnector.send(
            evmRequest.rpcUrl,
            "eth_getTransactionCount",
            listOf(evmRequest.from, "latest")
        )
        val nonce = BigInteger.valueOf(Integer.decode(transactionCountResponse.result.toString()).toLong())

        val chainId = evmConnector.send(evmRequest.rpcUrl, "eth_chainId", emptyList<String>())
        val parsedChainId = Numeric.toBigInt(chainId.result.toString()).toLong()

        val block = evmConnector.send(evmRequest.rpcUrl, "eth_getBlockByNumber", listOf("latest","true"))
        val blockData = (block.result as NonEip1559BlockData)
        val gasLimit = blockData.gasLimit
        println("gasLimit $gasLimit")
//        println(Integer.decode(gasLimit))



        val gasPrice = evmConnector.send(evmRequest.rpcUrl, "eth_gasPrice", emptyList<String>())
        println("Gas price ${gasPrice}")
        println("Gas price ${Integer.decode(gasPrice.result.toString())}")


        val maxPriorityFeePerGas = 47000 * Integer.decode(gasPrice.result.toString()) * sentTransaction.payload.toByteArray().size
        println("MAX PRIORITY FEE PER GAS $maxPriorityFeePerGas")

        val baseFeePerGas = blockData.baseFeePerGas

//        val decodedBaseFeePerGas = Integer.decode(baseFeePerGas)




        println("AFTER PREP")


        // Return the maximum priority fee per gas as a BigInteger.

        val transaction = RawTransaction.createTransaction(
            parsedChainId,
            nonce,
            // Seems appropriate for the hyperledger besu network
            BigInteger.valueOf(Numeric.toBigInt("0x47b760").toLong()),
            evmRequest.to,
            BigInteger.valueOf(0),
            sentTransaction.payload,
            BigInteger.valueOf(maxPriorityFeePerGas.toLong()),
            BigInteger.valueOf(515814755000)
        )

        val signer = Credentials.create("0x8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63")
        val signed = TxSignServiceImpl(signer).sign(transaction, parsedChainId)
        println("Passed Signing")
        println(Numeric.toHexString(signed))
        val tReceipt =
            evmConnector.send(evmRequest.rpcUrl, "eth_sendRawTransaction", listOf(Numeric.toHexString(signed)))
        // Exception Case When Contract is Being Created we need to wait the address
        return if (evmRequest.to.isEmpty()) {
            EvmResponse(evmRequest.flowId,queryCompletionContract(evmRequest.rpcUrl, tReceipt.result.toString()))
        } else {
            EvmResponse(evmRequest.flowId,tReceipt.result.toString())
        }
    }
}