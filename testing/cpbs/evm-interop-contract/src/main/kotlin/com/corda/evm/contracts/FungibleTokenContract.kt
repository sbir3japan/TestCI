package com.corda.evm.contracts

import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction


sealed  class  TokenCommand: Command {
    object Issue : TokenCommand()
    object Spend : TokenCommand()
}


@Suppress("unused")
class FungibleTokenContract: Contract {

    override fun verify(transaction: UtxoLedgerTransaction) {

    }


}