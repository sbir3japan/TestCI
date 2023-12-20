package net.corda.interop.evm.dispatcher.factory

import net.corda.interop.evm.EthereumConnector
import net.corda.interop.evm.dispatcher.CallDispatcher
import net.corda.interop.evm.dispatcher.EvmDispatcher
import net.corda.interop.evm.dispatcher.GetBalanceDispatcher
import net.corda.interop.evm.dispatcher.GetBlockByHash
import net.corda.interop.evm.dispatcher.GetBlockByNumber
import net.corda.interop.evm.dispatcher.GetTransactionByHash
import net.corda.interop.evm.dispatcher.GetTransactionReceiptDispatcher
import net.corda.interop.evm.dispatcher.SendRawTransactionDispatcher
import net.corda.interop.evm.dispatcher.WaitForTransactionDispatcher


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

    override fun getBlockByHash(evmConnector: EthereumConnector): EvmDispatcher {
        return GetBlockByHash(evmConnector)
    }

    override fun getBlockByNumber(evmConnector: EthereumConnector): EvmDispatcher {
        return GetBlockByNumber(evmConnector)
    }

    override fun getTransactionByHash(evmConnector: EthereumConnector): EvmDispatcher {
        return GetTransactionByHash(evmConnector)
    }

    override fun waitForTransactionDispatcher(evmConnector: EthereumConnector): EvmDispatcher {
       return WaitForTransactionDispatcher(evmConnector)
    }
}
