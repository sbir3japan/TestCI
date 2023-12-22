package com.r3.corda.demo.swaps.workflows.swap

import com.r3.corda.demo.swaps.contracts.swap.LockCommand
import com.r3.corda.demo.swaps.states.swap.LockState
import com.r3.corda.demo.swaps.states.swap.OwnableState
import com.r3.corda.demo.swaps.states.swap.UnlockData
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction

class UnlockTransactionAndObtainAssetSubFlow(
    private val lockedAsset: StateAndRef<OwnableState>,
    private val lockState: StateAndRef<LockState>,
    private val unlockData: UnlockData
): SubFlow<UtxoSignedTransaction> {

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    override fun call(): UtxoSignedTransaction {
        val myInfo = memberLookup.myInfo()

        val newOwner = memberLookup.lookup(lockState.state.contractState.assetRecipient)
            ?: throw IllegalArgumentException("The specified recipient does not resolve to a known Party")

        val unlockCommand = LockCommand.Unlock(unlockData)
        val signedTransaction = utxoLedgerService.createTransactionBuilder()
            .setNotary(lockState.state.notaryName)
            .addInputState(lockedAsset.ref)
            .addInputState(lockState.ref)
            .addSignatories(myInfo.ledgerKeys.first())
            .addOutputState(lockedAsset.state.contractState.withNewOwner(newOwner.ledgerKeys.first()))
            .addCommand(unlockCommand)
            .toSignedTransaction()

        return utxoLedgerService.finalize(signedTransaction, emptyList()).transaction
    }
}
