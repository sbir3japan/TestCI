package net.corda.interop.evm.dispatcher

import java.math.BigInteger
import net.corda.data.interop.evm.EvmRequest
import net.corda.data.interop.evm.EvmResponse
import net.corda.data.interop.evm.request.Transaction
import net.corda.interop.evm.EthereumConnector
import net.corda.interop.evm.GenericResponse
import net.corda.interop.evm.constants.GET_CHAIN_ID
import net.corda.interop.evm.constants.GET_TRANSACTION_COUNT
import net.corda.interop.evm.constants.LATEST
import net.corda.interop.evm.constants.SEND_RAW_TRANSACTION
import net.corda.interop.evm.constants.TEMPORARY_PRIVATE_KEY
import net.corda.interop.evm.encoder.TransactionEncoder
import org.slf4j.LoggerFactory
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.service.TxSignServiceImpl
import org.web3j.utils.Numeric

/**
 * Dispatcher used to send transaction.
 *
 * @param evmConnector The evmConnector class used to make rpc calls to the node
 */
class SendRawTransactionDispatcher(private val evmConnector: EthereumConnector) : EvmDispatcher {
    // This is used in absence of the crypto worker being able to sign these transactions for use
    private val temporaryPrivateKey = TEMPORARY_PRIVATE_KEY
    private val log = LoggerFactory.getLogger(SendRawTransactionDispatcher::class.java)


    override fun dispatch(evmRequest: EvmRequest): EvmResponse {
         val request = evmRequest.payload as Transaction
        val data = TransactionEncoder.encode(request.function, request.parameters)

        log.info("Transaction data: $data")
        val privateKey = request.options.privateKey ?: temporaryPrivateKey

        val address = Credentials.create(privateKey).address



        val transactionCountResponse = evmConnector.send<GenericResponse>(
            evmRequest.rpcUrl, GET_TRANSACTION_COUNT, listOf(address, LATEST)
        )
        val nonce = BigInteger.valueOf(Integer.decode(transactionCountResponse.result.toString()).toLong())
        val chainId = evmConnector.send<GenericResponse>(evmRequest.rpcUrl, GET_CHAIN_ID, emptyList<String>())
        val parsedChainId = Numeric.toBigInt(chainId.result.toString()).toLong()

//        public static Transaction1559 createTransaction(
//            long chainId,
//        BigInteger nonce,
//        BigInteger gasLimit,
//        String to,
//        BigInteger value,
//        String data,
//        BigInteger maxPriorityFeePerGas,
//        BigInteger maxFeePerGas) {
//
        val transaction = RawTransaction.createTransaction(
            parsedChainId,
            nonce,
            20000000000.toBigInteger(),
            evmRequest.to,
            BigInteger.valueOf(request.options.value.toLong()),
            data,
            BigInteger.valueOf(request.options.maxPriorityFeePerGas.toLong()),
            BigInteger.valueOf(request.options.maxFeePerGas.toLong())
        )

//        val transaction = RawTransaction.createTransaction(
//            nonce,
//            20000000000.toBigInteger(),
//            6721975.toBigInteger(),
//            evmRequest.to,
//            BigInteger.valueOf(request.options.value.toLong()),
//            data
//        )
        val signer = Credentials.create(temporaryPrivateKey)
        val signed = TxSignServiceImpl(signer).sign(transaction, parsedChainId)
        val tReceipt = evmConnector.send<GenericResponse>(
            evmRequest.rpcUrl, SEND_RAW_TRANSACTION, listOf(Numeric.toHexString(signed))
        )

        return EvmResponse(tReceipt.result.toString())
    }
}