//package net.r3.corda.web3j
//
//import net.corda.interop.evm.EthereumBlock
//import net.corda.interop.evm.EthereumConnector
//import net.corda.interop.evm.EvmRPCCall
//import net.corda.interop.evm.GenericResponse
//import net.corda.interop.evm.constants.ETH_GET_CODE
//import net.corda.interop.evm.constants.ETH_GET_BALANCE
//import net.corda.interop.evm.constants.GET_CHAIN_ID
//import net.corda.interop.evm.constants.LATEST
//import net.corda.interop.evm.constants.ETH_GET_BLOCK_BY_NUMBER
//import org.junit.jupiter.api.BeforeEach
//import org.junit.jupiter.api.Test
//import org.mockito.Mockito.`when`
//import org.mockito.Mockito.mock
//import org.junit.jupiter.api.Assertions.assertEquals
//
//
//class EthereumConnectorTests {
//
//    private lateinit var mockedEVMRpc: EvmRPCCall
//    private lateinit var evmConnector: EthereumConnector
//
//    private val rpcUrl = "http://127.0.0.1:8545"
//    private val mainAddress = "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"
//
//    @BeforeEach
//    fun setUp() {
//        mockedEVMRpc = mock(EvmRPCCall::class.java)
//        evmConnector = EthereumConnector(mockedEVMRpc)
//    }
//
//    @Test
//    fun getBalance() {
//
//        val jsonString = "{\"jsonrpc\":\"2.0\",\"id\":\"90.0\",\"result\":\"100000\"}"
//        `when`(
//            mockedEVMRpc.rpcCall(
//                rpcUrl,
//                ETH_GET_BALANCE,
//                listOf(mainAddress, LATEST)
//            )
//        ).thenReturn(jsonString)
//        val final = evmConnector.send<GenericResponse>(
//            rpcUrl,
//            ETH_GET_BALANCE,
//            listOf(mainAddress, LATEST)
//        )
//        assertEquals("100000", final.result)
//    }
//
//
//    @Test
//    fun getCode() {
//        val mockedEVMRpc = mock(EvmRPCCall::class.java)
//        val evmConnector = EthereumConnector(mockedEVMRpc)
//        val jsonString = "{\"jsonrpc\":\"2.0\",\"id\":\"90.0\",\"result\":\"0xfd2ds\"}"
//        `when`(
//            mockedEVMRpc.rpcCall(
//                rpcUrl,
//                ETH_GET_CODE,
//                listOf(mainAddress, "0x1")
//            )
//        ).thenReturn(jsonString)
//        val final = evmConnector.send<GenericResponse>(
//            rpcUrl,
//            ETH_GET_CODE,
//            listOf(mainAddress, "0x1")
//        )
//        assertEquals("0xfd2ds", final.result)
//    }
//
//
//    @Test
//    fun getChainId() {
//        val mockedEVMRpc = mock(EvmRPCCall::class.java)
//        val evmConnector = EthereumConnector(mockedEVMRpc)
//        val jsonString = "{\"jsonrpc\":\"2.0\",\"id\":\"90.0\",\"result\":\"1337\"}"
//        `when`(
//            mockedEVMRpc.rpcCall(
//                rpcUrl,
//                GET_CHAIN_ID,
//                emptyList<String>()
//            )
//        ).thenReturn(jsonString)
//        val final = evmConnector.send<GenericResponse>(rpcUrl, GET_CHAIN_ID, emptyList<String>())
//        assertEquals("1337", final.result)
//    }
//
//    @Test
//    fun getBlockByNumber() {
//        val jsonString = """
//        {
//            "id": "90.0",
//            "jsonrpc": "2.0",
//            "result": {
//                "number": "0x1",
//                "hash": "0x4c7b46fbe652b6d10cd6f68dc8516d581718bc1475d43899224ddb6651b0e5a5",
//                "parentHash": "0xb21ff4855220f22371ca4412429808abf9997afbd969d67f6451a6be244a0079",
//                "mixHash": "0x0000000000000000000000000000000000000000000000000000000000000000",
//                "nonce": "0x0000000000000000",
//                "sha3Uncles": "0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347",
//                "transactionsRoot": "0x74bf2a4cba9688e43bce3d9c972f68ef32158ad0eecefa0a47bf65c4dca3b913",
//                "stateRoot": "0xc52a1cef1ce0b958e044eae061d3f7e82d05fdfee700c3c41e08a4b24ec305ee",
//                "receiptsRoot": "0xe01b364aa5fb471c10231d7c1114a0c5b922d023686b04cd6e199b4d54683f9b",
//                "miner": "0x0000000000000000000000000000000000000000",
//                "difficulty": "0x0",
//                "totalDifficulty": "0x0",
//                "extraData": "0x",
//                "size": "0x3e8",
//                "gasLimit": "0x6691b7",
//                "gasUsed": "0x2773eb",
//                "timestamp": "0x656ddc60",
//                "transactions": [
//                    {
//                        "hash": "0x1ca6e71216a4c60fc1abe92a09ff1fdbc78ab0561fb5dbf1c7d3daf90703df74",
//                        "nonce": "0x0",
//                        "blockHash": "0x4c7b46fbe652b6d10cd6f68dc8516d581718bc1475d43899224ddb6651b0e5a5",
//                        "blockNumber": "0x1",
//                        "transactionIndex": "0x0",
//                        "from": "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73",
//                        "to": null,
//                        "value": "0x0",
//                        "gas": "0x6691b7",
//                        "gasPrice": "0x4a817c800",
//                        "v": "0xa96",
//                        "r": "0x717547d3782e959e1336ba5e066bb24373d83fc8a07be5b186ff42cc4e7c231b",
//                        "s": "0x6202af8bfd7d37374997a759f817da345c9f370d86c9d8cb2bb1e52073afc1b9"
//                    }
//                ],
//                "uncles": []
//            }
//        }
//    """.trimIndent()
//        val mockedEVMRpc = mock(EvmRPCCall::class.java)
//        val evmConnector = EthereumConnector(mockedEVMRpc)
//        val blockNumber = 1
//        val fullTransactionObjects = true
//
//        `when`(mockedEVMRpc.rpcCall(rpcUrl, ETH_GET_BLOCK_BY_NUMBER, listOf(blockNumber, fullTransactionObjects))).thenReturn(jsonString)
//
//        val final = evmConnector.send<EthereumBlock>(rpcUrl, ETH_GET_BLOCK_BY_NUMBER, listOf(blockNumber, fullTransactionObjects))
//        println(final)
//
//    }
//
//}