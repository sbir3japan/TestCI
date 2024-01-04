package com.r3.corda.demo.swaps.states.swap

import com.r3.corda.demo.swaps.IUnlockEventEncoder
import com.r3.corda.demo.swaps.contracts.swap.LockStateContract
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.ContractState
import java.security.PublicKey
import java.util.UUID

// *********
// * State *
// *********


@CordaSerializable
@BelongsToContract(LockStateContract::class)
class LockState(
    val assetSender: PublicKey,
    val assetRecipient: PublicKey,
    val notary: PublicKey,
    val approvedValidators: List<PublicKey>,
    val signaturesThreshold: Int,
    val unlockEvent: IUnlockEventEncoder,
    val linearId: UUID = UUID.randomUUID(),
): ContractState {
    override fun getParticipants(): List<PublicKey> {
        return listOf(assetSender, assetRecipient)
    }
}

