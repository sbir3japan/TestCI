package com.corda.evm.states


import com.corda.evm.contracts.FungibleTokenContract
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.ContractState
import java.security.PublicKey
import java.util.*

// *********
// * State *
// *********


@BelongsToContract(FungibleTokenContract::class)
@CordaSerializable
data class FungibleTokenState(
    val valuation: Int,
    val maintainer: PublicKey,
    val linearId: UUID = UUID.randomUUID(),
    val fractionDigits: Int,
    val symbol: String,
    val balances: Map<PublicKey,Long>,
    private val participants : List<PublicKey>): ContractState {
        override fun getParticipants(): List<PublicKey> = participants
    }
