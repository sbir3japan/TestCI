package net.corda.interop.evm.dispatcher

import net.corda.data.interop.evm.EvmRequest
import net.corda.data.interop.evm.EvmResponse
import net.corda.data.interop.evm.request.GetBalance
import net.corda.interop.evm.EthereumConnector
import net.corda.interop.evm.GenericResponse
import net.corda.interop.evm.constants.ETH_GET_BALANCE
import net.corda.interop.evm.decoder.TransactionDecoder
import java.math.BigInteger


/**
 * Dispatcher used to make call methods to a Generic EVM Node
 *
 * @param evmConnector The evmConnector class used to make rpc calls to the node
 */
class GetBalanceDispatcher(private val evmConnector: EthereumConnector) : EvmDispatcher {
    override fun dispatch(evmRequest: EvmRequest): EvmResponse {
        val request = evmRequest.payload as GetBalance
        // EVM Expects both data & input
        val resp = evmConnector.send<GenericResponse>(evmRequest.rpcUrl, ETH_GET_BALANCE, listOf(request.address, request.blockNumber))
        // TODO: We can manually decode this
        return EvmResponse(BigInteger(resp.result!!.replace("0x",""), 16))
    }
}