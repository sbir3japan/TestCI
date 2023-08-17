package com.r3.corda.ethereumConnector

import net.corda.interop.web3j.internal.EthereumConnector
import net.corda.interop.web3j.internal.EvmRPCCall
import net.corda.interop.web3j.internal.RPCResponse
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class EthereumConnectorTests {

    @Test
    fun getCode(){
        val mockedEVMRpc = mock(EvmRPCCall::class.java)
        val evmConnector = EthereumConnector(mockedEVMRpc)
        val jsonString = "{\"jsonrpc\":\"2.0\",\"id\":\"90.0\",\"result\":\"0xfd2ds\"}"

        `when`(mockedEVMRpc.rpcCall("http://127.0.0.1:8545","evm_getCode", listOf("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73","0x1"))).thenReturn(RPCResponse(true,jsonString))
        println(evmConnector)
        val final = evmConnector.send("http://127.0.0.1:8545","evm_getCode", listOf("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73","0x1"))
        println(final)
    }


    @Test
    fun getChainId(){
        val mockedEVMRpc = mock(EvmRPCCall::class.java)
        val evmConnector = EthereumConnector(mockedEVMRpc)
        val jsonString = "{\"jsonrpc\":\"2.0\",\"id\":\"90.0\",\"result\":\"1337\"}"
        `when`(mockedEVMRpc.rpcCall("http://127.0.0.1:8545","eth_chainId", emptyList<String>())).thenReturn(RPCResponse(true,jsonString))
        println(evmConnector)
        val final = evmConnector.send("http://127.0.0.1:8545","eth_chainId", emptyList<String>())
        assert(final.result=="1337")
    }



    @Test
    fun isSyncing(){
        val mockedEVMRpc = mock(EvmRPCCall::class.java)
        val evmConnector = EthereumConnector(mockedEVMRpc)
        val jsonString = "{\"jsonrpc\":\"2.0\",\"id\":\"90.0\",\"result\":\"false\"}"
        `when`(mockedEVMRpc.rpcCall("http://127.0.0.1:8545","eth_syncing", emptyList<String>())).thenReturn(RPCResponse(true,jsonString))
        println(evmConnector)
        val final = evmConnector.send("http://127.0.0.1:8545","eth_syncing", emptyList<String>())
        assert(final.result=="false")
    }






//    AVALANCHEGO_EXEC_PATH="${HOME}/Documents/avalanche/avalanchego/build/avalanchego"
}