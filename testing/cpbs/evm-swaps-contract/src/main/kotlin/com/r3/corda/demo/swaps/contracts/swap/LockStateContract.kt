package com.r3.corda.demo.swaps.contracts.swap

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.utxo.ContractState
import java.security.PublicKey
import java.util.*

// *********
// * State *
// *********


@CordaSerializable
data class LockState(
    val assetSender: PublicKey,
    val assetRecipient: PublicKey,
    val notary: PublicKey,
    val approvedValidators: List<PublicKey>,
    val signaturesThreshold: Int,
//    val unlockEvent: IUnlockEventEncoder,
    val linearId: UUID = UUID.randomUUID(),
    private val participants : List<PublicKey>): ContractState {
    override fun getParticipants(): List<PublicKey> = participants
}
