package net.corda.interop.evm.dispatcher

import net.corda.data.interop.evm.EvmRequest
import net.corda.data.interop.evm.EvmResponse
import net.corda.data.interop.evm.request.GetTransactionByHash
import net.corda.data.interop.evm.response.TransactionObject
import net.corda.interop.evm.EthereumConnector
import net.corda.interop.evm.TransactionResponse
import net.corda.interop.evm.constants.GET_TRANSACTION_BY_HASH

/**
 * Dispatcher used to get transaction by hash.
 *
 * @param evmConnector The evmConnector class used to make rpc calls to the node
 */
class GetTransactionByHash(private val evmConnector: EthereumConnector) : EvmDispatcher {
    override fun dispatch(evmRequest: EvmRequest): EvmResponse {
        val request = evmRequest.payload as GetTransactionByHash

        val resp = evmConnector.send<TransactionResponse>(
            evmRequest.rpcUrl,
            GET_TRANSACTION_BY_HASH,
            listOf(request.blockHash)
        )
        val result = resp.result

        val transaction = TransactionObject.newBuilder()
            .setBlockHash(result.blockHash)
            .setBlockNumber(result.blockNumber)
            .setFrom(result.from)
            .setGas(result.gas)
            .setGasPrice(result.gasPrice)
            .setMaxFeePerGas(result.maxFeePerGas?:"")
            .setMaxPriorityFeePerGas(result.maxPriorityFeePerGas?:"")
            .setHash(result.hash)
            .setInput(result.input)
            .setNonce(result.nonce)
            .setTo(result.to)
            .setTransactionIndex(result.transactionIndex)
            .setValue(result.value)
            .setV(result.v)
            .setR(result.r)
            .setS(result.s)
            .build()


        return EvmResponse(transaction)
    }
}