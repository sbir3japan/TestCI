package com.r3.corda.demo.interop.evm.state


import net.corda.v5.ledger.contracts.BelongsToContract
import net.corda.v5.ledger.utxo.ContractState
import java.security.PublicKey
import java.util.*
import com.r3.corda.demo.interop.evm.contracts.FungibleTokenContract

// *********
// * State *
// *********

data class FungibleTokenState(
    val valuation: Int,
    val maintainer: PublicKey,
    val linearId: UUID = UUID.randomUUID(),
    val fractionDigits: Int,
    val symbol: String,
    val balances: Map<PublicKey, Long>,
    private val participants : List<PublicKey>): ContractState {
        override fun getParticipants(): List<PublicKey> = participants
    }
