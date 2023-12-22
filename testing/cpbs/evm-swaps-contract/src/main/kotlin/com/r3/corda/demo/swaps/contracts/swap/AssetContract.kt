package com.r3.corda.demo.swaps.contracts.swap

import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

class AssetContract: Contract {

    internal companion object {
        const val REQUIRES_ONE_OUTPUT = "The transaction requires one output"
        const val REQUIRES_ZERO_INDEX_OUTPUT = "The transaction requires one output at zero index"
    }

    interface AssetCommands : Command {
        class Create: AssetCommands
    }

    override fun verify(transaction: UtxoLedgerTransaction) {
//        val outputStateAndRef = transaction.outputStateAndRefs.singleOrNull()
//            ?: throw CordaRuntimeException(REQUIRES_ONE_OUTPUT)
//
//        outputStateAndRef.ref.index == 0 || throw CordaRuntimeException(REQUIRES_ZERO_INDEX_OUTPUT)
    }
}
