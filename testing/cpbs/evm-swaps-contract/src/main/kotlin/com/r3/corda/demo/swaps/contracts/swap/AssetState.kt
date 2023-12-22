package com.r3.corda.demo.swaps.contracts.swap

import com.r3.corda.demo.swaps.states.swap.OwnableState
import net.corda.v5.ledger.utxo.BelongsToContract
import java.security.PublicKey
import java.util.*

@BelongsToContract(AssetContract::class)
class AssetState(
    val assetName: String,
    owner: PublicKey,
    linearId: UUID = UUID.randomUUID(),
    participants: List<PublicKey> = listOf(owner)
) : OwnableState(owner, linearId, participants) {

    override fun withNewOwner(newOwner: PublicKey): AssetState {
        return AssetState(assetName, newOwner, linearId, setOf(owner, newOwner).toList())
    }
}
