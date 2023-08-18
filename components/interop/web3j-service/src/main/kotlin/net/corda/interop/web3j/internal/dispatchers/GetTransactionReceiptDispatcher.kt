package net.corda.interop.web3j.internal.dispatchers

import net.corda.data.interop.evm.EvmRequest
import net.corda.data.interop.evm.EvmResponse
import net.corda.interop.web3j.EvmDispatcher

abstract class GetTransactionReceiptDispatcher : EvmDispatcher {
    abstract override fun dispatch(evmRequest: EvmRequest): EvmResponse

}