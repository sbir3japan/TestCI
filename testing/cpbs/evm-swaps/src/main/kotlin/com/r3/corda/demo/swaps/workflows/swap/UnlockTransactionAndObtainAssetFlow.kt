package com.r3.corda.demo.swaps.workflows.swap

import com.r3.corda.demo.swaps.contracts.swap.LockCommand
import com.r3.corda.demo.swaps.contracts.swap.LockStateContract
import com.r3.corda.demo.swaps.states.swap.LockState
import com.r3.corda.demo.swaps.states.swap.OwnableState
import com.r3.corda.demo.swaps.states.swap.UnlockData
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.UtxoLedgerService
import java.time.Duration
import java.time.Instant

/**
 * Initiating flow which transfers the Corda asset to the new owner (calling party) using proofs generated by
 * approved Corda validators.
 *
 * @param lockedAsset the asset to be unlocked
 * @param lockState the lock state which contains the proofs
 * @param unlockData the unlock data which contains the proofs
 */
@InitiatingFlow(protocol = "unlock-tx-obtain-asset-flow")
class UnlockTransactionAndObtainAssetSubFlow(
    private val lockedAsset: StateAndRef<OwnableState>,
    private val lockState: StateAndRef<LockState>,
    private val unlockData: UnlockData
): SubFlow<SecureHash> {

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    @Suspendable
    override fun call(): SecureHash {

        val myInfo = memberLookup.myInfo()

        val newOwner = memberLookup.lookup(lockState.state.contractState.assetRecipient)
            ?: throw IllegalArgumentException("The specified recipient does not resolve to a known Party")

        val unlockCommand = LockStateContract.LockCommands.Unlock(unlockData)

        val builder = ledgerService.createTransactionBuilder()
            .setNotary(lockState.state.notaryName)
            // not using setTimeWindowBetween(Instant.now(), Instant.MAX) to avoid a bug causing overflow in avro conversion
            .setTimeWindowUntil(Instant.now() + Duration.ofDays(7))
            .addInputStates(lockedAsset.ref, lockState.ref)
            // lockedAsset asset's owner is a composite key made of sender and recipient
            .addOutputState(lockedAsset.state.contractState.withNewOwner(newOwner.ledgerKeys.first()))
            .addCommand(unlockCommand)
            .addSignatories(myInfo.ledgerKeys.first())

        val signedTransaction = builder.toSignedTransaction()

        // REVIEW: correctness of use of emptyList()
        val result = ledgerService.finalize(signedTransaction, emptyList())

        return result.transaction.id
    }
}
