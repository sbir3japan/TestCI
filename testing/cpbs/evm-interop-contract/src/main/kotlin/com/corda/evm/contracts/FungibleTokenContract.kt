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

    // verify() function is used to apply contract rules to the transaction.
    override fun verify(transaction: UtxoLedgerTransaction) {

        val command = transaction.commands.filterIsInstance<TokenCommand>().single()
        when(command) {
            // Rules applied only to transactions with the Create Command.
            is TokenCommand.Issue -> {

            }
            is TokenCommand.Spend -> {

            }

        }
    }


}