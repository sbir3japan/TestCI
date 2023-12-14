package com.r3.corda.demo.swaps.contracts.swap

import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction


sealed  class  LockCommand: Command {
    object Lock : LockCommand()
    object Revert : LockCommand()
    object Unlock : LockCommand()
}


@Suppress("unused")
class LockStateContract: Contract {

    override fun verify(transaction: UtxoLedgerTransaction) {

    }


}