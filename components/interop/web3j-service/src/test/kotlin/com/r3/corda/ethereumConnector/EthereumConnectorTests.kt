package com.r3.corda.ethereumConnector

import net.corda.interop.web3j.internal.EthereumConnector
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito
import okhttp3.OkHttpClient


class EthereumConnectorTests {



    @Test
    fun helloWorld(){
        val httpClient = Mockito.mock<OkHttpClient>()
        val evmConnector = EthereumConnector(httpClient)




        val resp = evmConnector.send("http://127.0.0.1:8545","eth_getCode", emptyList<String>())
        println("resp $resp")

        assert(true)
    }

}