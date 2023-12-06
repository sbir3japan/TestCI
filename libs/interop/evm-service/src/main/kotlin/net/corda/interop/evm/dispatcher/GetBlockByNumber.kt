package net.corda.interop.evm.dispatcher

import net.corda.data.interop.evm.EvmRequest
import net.corda.data.interop.evm.EvmResponse
import net.corda.data.interop.evm.request.GetBlockByNumber
import net.corda.interop.evm.EthereumBlock
import net.corda.interop.evm.EthereumConnector
import net.corda.interop.evm.constants.ETH_GET_BLOCK_BY_NUMBER
import net.corda.data.interop.evm.response.Block


/**
 * Dispatcher used to Get a Block By Number
 *
 * @param evmConnector The evmConnector class used to make rpc calls to the node
 */
class GetBlockByNumber(private val evmConnector: EthereumConnector) : EvmDispatcher {
    override fun dispatch(evmRequest: EvmRequest): EvmResponse {
        val request = evmRequest.payload as GetBlockByNumber
        // EVM Expects both data & input
        val resp = evmConnector.send<EthereumBlock>(
            evmRequest.rpcUrl,
            ETH_GET_BLOCK_BY_NUMBER,
            listOf(request.blockNumber.toBigInteger(), request.fullTransactionObjects)
        )

        val blockInfo = resp.result
        val block =
            Block.newBuilder().setNumber(blockInfo.number).setHash(blockInfo.hash).setParentHash(blockInfo.parentHash)
                .setNonce(blockInfo.nonce).setSha3Uncles(blockInfo.sha3Uncles).setLogsBloom(blockInfo.logsBloom)
                .setTransactionsRoot(blockInfo.transactionsRoot).setStateRoot(blockInfo.stateRoot)
                .setReceiptsRoot(blockInfo.receiptsRoot).setMiner(blockInfo.miner).setDifficulty(blockInfo.difficulty)
                .setTotalDifficulty(blockInfo.totalDifficulty).setExtraData(blockInfo.extraData).setSize(blockInfo.size)
                .setTransactions(blockInfo.transactions).setUncles(blockInfo.uncles)
                .setMaxFeePerGas("").setMaxPriorityFeePerGas("")
                .setGasUsed(blockInfo.gasUsed).setTimestamp(blockInfo.timestamp)
                .setGasLimit(blockInfo.gasLimit).setGasUsed(blockInfo.gasUsed).setTimestamp(blockInfo.timestamp).build()

        return EvmResponse(block)
    }
}