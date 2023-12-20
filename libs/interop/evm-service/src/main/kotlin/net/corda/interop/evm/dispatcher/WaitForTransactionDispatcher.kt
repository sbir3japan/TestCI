package net.corda.interop.evm.dispatcher

import net.corda.data.interop.evm.EvmRequest
import net.corda.data.interop.evm.EvmResponse
import net.corda.data.interop.evm.request.WaitForTransaction
import net.corda.data.interop.evm.response.TransactionReceipt
import net.corda.interop.evm.EthereumConnector
import net.corda.interop.evm.Response
import net.corda.interop.evm.constants.GET_TRANSACTION_RECEIPT
import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Dispatcher used to get transaction receipt.
 *
 * @param evmConnector The evmConnector class used to make rpc calls to the node
 */
class WaitForTransactionDispatcher(private val evmConnector: EthereumConnector) : EvmDispatcher {
    override fun dispatch(evmRequest: EvmRequest): EvmResponse {
        val getTransactionReceipt = evmRequest.payload as WaitForTransaction

        var resp: Response? = null
        var found = false
        val maxErrors = 10
        var errors = 0

        while (!found) {
            if (errors > maxErrors) {
                throw CordaRuntimeException("Error waiting for transaction receipt")
            }
            try {
                resp = evmConnector.send<Response>(
                    evmRequest.rpcUrl,
                    GET_TRANSACTION_RECEIPT,
                    listOf(getTransactionReceipt.transactionHash)
                )
                found = true
            } catch (e: Exception) {
                errors++
                Thread.sleep(2000)

            }
        }

        val result = resp!!.result

        val transactionReceipt = TransactionReceipt.newBuilder()
            .setTransactionHash(result.transactionHash)
            .setTransactionIndex(result.transactionIndex)
            .setBlockNumber(result.blockNumber.replace("0x", ""))
            .setBlockHash(result.blockHash)
            .setContractAddress(result.contractAddress)
            .setCumulativeGasUsed(result.cumulativeGasUsed)
            .setEffectiveGasPrice(result.effectiveGasPrice)
            .setFrom(result.from).setGasUsed(result.gasUsed)
            .setLogsBloom(result.logsBloom)
            .setStatus(Integer.parseInt(result.status.replace("0x", ""), 16) != 0)
            .setTo(result.to).setType(result.type)
            .setLogs(emptyList())
            .setGasUsed(result.gasUsed)
            .setType(result.type?:"")
            .build()

        return EvmResponse(transactionReceipt)
    }
}