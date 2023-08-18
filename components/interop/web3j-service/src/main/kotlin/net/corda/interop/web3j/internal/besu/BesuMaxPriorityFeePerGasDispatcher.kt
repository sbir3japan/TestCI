package net.corda.interop.web3j.internal.besu

import net.corda.data.interop.evm.EvmRequest
import net.corda.data.interop.evm.EvmResponse
import net.corda.data.interop.evm.request.GetTransactionReceipt
import net.corda.interop.web3j.EvmDispatcher
import net.corda.interop.web3j.internal.EthereumConnector
import net.corda.interop.web3j.internal.dispatchers.GetBalanceDispatcher
import java.math.BigInteger

class BesuMaxPriorityFeePerGasDispatcher(val evmConnector: EthereumConnector) : EvmDispatcher {
    /**
     * Retrieves the balance of a given Ethereum address using the provided RPC connection.
     *
     * @param rpcConnection The RPC connection URL for Ethereum communication.
     * @param from The Ethereum address for which to retrieve the balance.
     * @return The balance of the specified Ethereum address as a string.
     */
    override fun dispatch(evmRequest: EvmRequest): EvmResponse {
        // Send an RPC request to retrieve the maximum priority fee per gas.
        val resp = evmConnector.send(evmRequest.rpcUrl, "eth_maxPriorityFeePerGas", emptyList<String>())

        // Return the maximum priority fee per gas as a BigInteger.
        return EvmResponse(evmRequest.flowId,Integer.decode(resp.result.toString()).toLong().toString())
    }
}