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
open class OwnableState(
    open val owner: PublicKey,
    open val linearId: UUID = UUID.randomUUID(),
    private val participants : List<PublicKey> = listOf(owner)
): ContractState {
    override fun getParticipants(): List<PublicKey> = participants

    open fun withNewOwner(newOwner: PublicKey): OwnableState {
        return OwnableState(newOwner, linearId, setOf(owner, newOwner).toList())
    }
}
