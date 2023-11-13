package com.r3.corda.demo.interop.evm.contracts

import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction


sealed  class  TokenCommand: Command {
    object Issue : TokenCommand()
    object Spend : TokenCommand()
}

class FungibleTokenContract: Contract {

    // verify() function is used to apply contract rules to the transaction.
    override fun verify(transaction: UtxoLedgerTransaction) {

//        val command = transaction.commands.filterIsInstance<TokenCommand>().single()
//        when(command) {
//            // Rules applied only to transactions with the Create Command.
//            is TokenCommand.Issue -> {
//
//            }
//            is TokenCommand.Spend -> {
//
//            }
//            else -> {
//                throw CordaRuntimeException("Command not allowed.")
//            }
//        }
    }

    // Helper function to allow writing constraints in the Corda 4 '"text" using (boolean)' style
    private infix fun String.using(expr: Boolean) {
        if (!expr) throw CordaRuntimeException("Failed requirement: $this")
    }

    // Helper function to allow writing constraints in '"text" using {lambda}' style where the last expression
    // in the lambda is a boolean.
    private infix fun String.using(expr: () -> Boolean) {
        if (!expr.invoke()) throw CordaRuntimeException("Failed requirement: $this")
    }
}