package com.r3.corda.demo.swaps.states.swap

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.utxo.ContractState
import java.security.PublicKey
import java.util.UUID

// *********
// * State *
// *********


@CordaSerializable
data class OwnableState(
    val owner: MemberX500Name,
    val linearId: UUID,
    private val participants : List<PublicKey>): ContractState {
    override fun getParticipants(): List<PublicKey> = participants

    fun withNewOwner(newOwner: MemberX500Name): OwnableState {
        return copy(owner = newOwner)
    }


}
