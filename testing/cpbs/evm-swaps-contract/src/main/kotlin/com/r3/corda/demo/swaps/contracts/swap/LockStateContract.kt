package com.r3.corda.demo.swaps.contracts.swap

import com.r3.corda.demo.swaps.states.swap.LockState
import com.r3.corda.demo.swaps.states.swap.OwnableState
import com.r3.corda.demo.swaps.states.swap.SerializableTransactionReceipt
import com.r3.corda.demo.swaps.states.swap.UnlockData
import com.r3.corda.demo.swaps.utils.trie.PatriciaTrie
import net.corda.v5.application.interop.evm.TransactionReceipt
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import org.rlp.RlpEncoder
import org.rlp.RlpList
import org.rlp.RlpString
import org.utils.Numeric
import java.math.BigInteger
import java.security.PublicKey

@Suppress("unused")
class LockStateContract: Contract {

    override fun verify(tx: UtxoLedgerTransaction) {
        val cmd = tx.commands.mapNotNull { it as LockCommands }.singleOrNull()
        require (cmd != null) { "Only one LockCommand can be used" }
        require (tx.outputContractStates.filterIsInstance<OwnableState>().size == 1) { "Only one asset state can be locked or reverted/unlocked" }

        when (cmd) {
            is LockCommands.Lock -> verifyLockCommand(tx)
            is LockCommands.Revert -> verifyRevertCommand(tx, cmd)
            is LockCommands.Unlock -> verifyUnlockCommand(tx, cmd)
        }
    }

    private fun verifyUnlockCommand(tx: UtxoLedgerTransaction, cmd: LockCommands.Unlock) {
        val inputsTxHash = tx.inputStateRefs.map { it.transactionId }.distinct().singleOrNull()
            ?: throw IllegalArgumentException("Inputs from multiple transactions is not supported")

        val unlockedAssetState = tx.outputContractStates.filterIsInstance<OwnableState>().single()
        val lockState = tx.inputContractStates.filterIsInstance<LockState>().single()
        val txIndexKey = RlpEncoder.encode(
            RlpString.create(
                cmd.proof.transactionReceipt.transactionIndex.toLong()
            )
        )
        val receiptsRoot = Numeric.hexStringToByteArray(cmd.proof.receiptsRootHash)
        val leafData = cmd.proof.transactionReceipt.encoded()

        // Ensure only two input states exist
        require(tx.inputContractStates.size == 2) {
            "Only two input states can exist"
        }

        // Check if the recipient is valid
        require(unlockedAssetState.owner == lockState.assetRecipient) {
            "Invalid recipient for this command"
        }

        // Verify EVM Transfer event validation by minimum number of validators
        require(cmd.proof.validatorSignatures.size >= lockState.signaturesThreshold) {
            "EVM Transfer event has not been validated by the minimum number of validators"
        }


        // Ensure the transaction receipt contains the expected unlock event
        require(lockState.unlockEvent.transferEvent(inputsTxHash).isFoundIn(cmd.proof.transactionReceipt)) {
            "The transaction receipt does not contain the expected unlock event"
        }

        // Validate the transaction receipts merkle proof
        require(PatriciaTrie.verifyMerkleProof(receiptsRoot, txIndexKey, leafData, cmd.proof.merkleProof)) {
            "The transaction receipts merkle proof failed to validate"
        }

        // Verify validator signatures for block inclusion
        require(verifyValidatorSignatures(cmd.proof.validatorSignatures, receiptsRoot, lockState.approvedValidators)) {
            "One or more validator signatures failed to verify block inclusion"
        }

        // Ensure only two input states exist
        require(tx.inputContractStates.size == 2) {
            "Only two input states can exist"
        }

        // Check if the recipient is valid
        require(unlockedAssetState.owner == lockState.assetRecipient) {
            "Invalid recipient for this command"
        }

        // Verify EVM Transfer event validation by minimum number of validators
        require(cmd.proof.validatorSignatures.size >= lockState.signaturesThreshold) {
            "EVM Transfer event has not been validated by the minimum number of validators"
        }

        // Ensure the transaction receipt contains the expected unlock event
        require(lockState.unlockEvent.transferEvent(inputsTxHash).isFoundIn(cmd.proof.transactionReceipt)) {
            "The transaction receipt does not contain the expected unlock event"
        }

        // Validate the transaction receipts merkle proof
        require(PatriciaTrie.verifyMerkleProof(receiptsRoot, txIndexKey, leafData, cmd.proof.merkleProof)) {
            "The transaction receipts merkle proof failed to validate"
        }

        // Verify validator signatures for block inclusion
        require(verifyValidatorSignatures(cmd.proof.validatorSignatures, receiptsRoot, lockState.approvedValidators)) {
            "One or more validator signatures failed to verify block inclusion"
        }

    }

    private fun verifyValidatorSignatures(sigs: List<DigitalSignature.WithKeyId>, signableData: ByteArray, approvedValidators: List<PublicKey>): Boolean {
        // TODO: convert to C5 code
//        sigs.forEach {
//            val validator = it.by
//            if (!approvedValidators.contains(validator) || !it.verify(signableData))
//                return false
//        }

        return true
    }

    private fun verifyRevertCommand(tx: UtxoLedgerTransaction, cmd: LockCommands.Revert) {
        TODO("Not yet implemented")
    }

    private fun verifyLockCommand(tx: UtxoLedgerTransaction) {
        // Ensure there are no input states of type LockState
        require(tx.inputContractStates.filterIsInstance<LockState>().isEmpty()) {
            "Transaction cannot have any input states of type net.corda.swap.contracts.LockState"
        }

        // Ensure exactly one LockState is generated in the output
        val outputLockStates = tx.outputContractStates.filterIsInstance<LockState>()
        require(outputLockStates.size == 1) {
            "Only one net.corda.swap.contracts.LockState can be generated"
        }

        // Ensure exactly two output states are generated, including the LockState
        require(tx.outputContractStates.size == 2) {
            "Exactly one asset state can be generated alongside a net.corda.swap.contracts.LockState"
        }

        // Fetch the single instances of locked asset state and lock state
        val lockedAssetState = tx.outputContractStates.filterIsInstance<OwnableState>().single()
        val lockState = outputLockStates.single()

        // Ensure the asset state is owned by the correct composite key
//        require((lockedAssetState.owner as CompositeKey).isFulfilledBy(
//            setOf(lockState.assetSender, lockState.assetRecipient)
//        )) {
//            "Asset state needs to be owned by the composite key created from original owner and new owner public keys"
//        }

        // Check if the number of validator signatures is appropriate
        require(lockState.signaturesThreshold <= lockState.approvedValidators.size) {
            "Required number of validator signatures is greater than the number of approved validators"
        }

        // Additional checks (like forward/backward events) can be added here
    }

    interface LockCommands : Command {
        class Lock : LockCommands {}
        class Revert(val proof: UnlockData) : LockCommands {}
        class Unlock(val proof: UnlockData) : LockCommands {}
    }
}


fun SerializableTransactionReceipt.encoded() : ByteArray {
//fun TransactionReceipt.encoded() : ByteArray {
    fun serializeLog(log: net.corda.v5.application.interop.evm.Log): RlpList {
        val address = Numeric.hexStringToByteArray(log.address)
        val topics = log.topics.map { topic -> Numeric.hexStringToByteArray(topic) }

        require(address.size == 20) { "Invalid contract address size (${address.size})" }
        require(topics.isNotEmpty() && topics.all { it.size == 32}) { "Invalid topics length or size" }

        return RlpList(
            listOf(
            RlpString.create(address),
            RlpList(topics.map { topic -> RlpString.create(topic) }),
            RlpString.create(Numeric.hexStringToByteArray(log.data))
            )
        )
    }

    return RlpEncoder.encode(RlpList(listOf(
        RlpString.create(if(this.status) BigInteger.ONE else BigInteger.ZERO),
        RlpString.create(this.cumulativeGasUsed),
        RlpString.create(Numeric.hexStringToByteArray(this.logsBloom)),
        RlpList(this.logs.map { serializeLog(it) })
    )))
}
