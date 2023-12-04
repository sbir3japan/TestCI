package net.corda.interop.evm.dispatcher.factory

import net.corda.interop.evm.EthereumConnector
import net.corda.interop.evm.dispatcher.*

object GenericDispatcherFactory : DispatcherFactory {


    override fun callDispatcher(evmConnector: EthereumConnector): EvmDispatcher {
        return CallDispatcher(evmConnector)
    }


    override fun getTransactionByReceiptDispatcher(evmConnector: EthereumConnector): EvmDispatcher {
        return GetTransactionReceiptDispatcher(evmConnector)
    }


    override fun sendRawTransactionDispatcher(evmConnector: EthereumConnector): EvmDispatcher {
        return SendRawTransactionDispatcher(evmConnector)
    }

    override fun getBalanceDispatcher(evmConnector: EthereumConnector): EvmDispatcher {
        return GetBalanceDispatcher(evmConnector)
    }
}
