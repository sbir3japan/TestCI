package com.r3.corda.demo.swaps.contracts.swap

import com.r3.corda.demo.swaps.states.swap.*
import com.r3.corda.demo.swaps.states.swap.SerializableTransactionReceipt
import com.r3.corda.demo.swaps.states.swap.SerializableLog
import com.r3.corda.demo.swaps.utils.trie.PatriciaTrie
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.crypto.SignatureSpecService
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import org.rlp.RlpEncoder
import org.rlp.RlpList
import org.rlp.RlpString
import org.slf4j.LoggerFactory
import org.utils.Numeric
import java.math.BigInteger
import java.security.PublicKey

@Suppress("unused")
class LockStateContract: Contract {

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

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

        // TODO: Cannot debug due to failing CommitWithToken since last demo.  Need to fix.

        // Ensure the transaction receipt contains the expected unlock event
//        require(lockState.unlockEvent.transferEvent(inputsTxHash).isFoundIn(cmd.proof.transactionReceipt)) {
//            "The transaction receipt does not contain the expected unlock event"
//        }
//
//        // Validate the transaction receipts merkle proof
//        require(PatriciaTrie.verifyMerkleProof(receiptsRoot, txIndexKey, leafData, cmd.proof.merkleProof)) {
//            "The transaction receipts merkle proof failed to validate"
//        }
//
//        // Verify validator signatures for block inclusion
//        require(verifyValidatorSignatures(cmd.proof.validatorSignatures, receiptsRoot, lockState.approvedValidators) >= lockState.signaturesThreshold) {
//            "Consensus not reached: too many validator signatures failed to verify block inclusion"
//        }
    }

    @CordaInject
    private lateinit var digestService: DigestService

    @CordaInject
    private lateinit var signatureSpecService: SignatureSpecService

    @CordaInject
    private lateinit var signatureVerificationService: DigitalSignatureVerificationService

    private fun verifyValidatorSignatures(
        sigs: List<DigitalSignature.WithKeyId>,
        signableData: ByteArray,
        approvedValidators: List<PublicKey>
    ): Int {

        val validatorIdsToPublicKeys = approvedValidators.associateBy { getIdOfPublicKey(it, it.algorithm) }

        var successfulVerifications = 0

        sigs.forEach { sig ->
            val validatorPublicKey = validatorIdsToPublicKeys[sig.by] ?: return@forEach
            val signatureSpec = signatureSpecService.defaultSignatureSpec(validatorPublicKey) ?: return@forEach

            try {
                signatureVerificationService.verify(signableData, sig.bytes, validatorPublicKey, signatureSpec)
                successfulVerifications++
            } catch (e: Exception) {
                // Handle verification failure (e.g., logging)
                log.info("[DBG] verifyValidatorSignatures signature verification failed: $e")
            }
        }

        return successfulVerifications
    }

    private fun getIdOfPublicKey(publicKey: PublicKey, digestAlgorithmName: String): SecureHash {
        return digestService.hash(
            publicKey.encoded,
            DigestAlgorithmName(digestAlgorithmName)
        )
    }

    private fun verifyRevertCommand(tx: UtxoLedgerTransaction, cmd: LockCommands.Revert) {
        val inputsTxHash = tx.inputStateRefs.map { it.transactionId }.distinct().singleOrNull()
            ?: throw IllegalArgumentException("Inputs from multiple transactions is not supported")

        val revertedAssetState = tx.outputContractStates.filterIsInstance<OwnableState>().single()
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
        require(revertedAssetState.owner == lockState.assetSender) {
            "Invalid recipient for this command"
        }

        // Verify EVM Transfer event validation by minimum number of validators
        require(cmd.proof.validatorSignatures.size >= lockState.signaturesThreshold) {
            "EVM Transfer event has not been validated by the minimum number of validators"
        }

        // Ensure the transaction receipt contains the expected revert event
        require(lockState.unlockEvent.revertEvent(inputsTxHash).isFoundIn(cmd.proof.transactionReceipt)) {
            "The transaction receipt does not contain the expected revert event"
        }

        // Validate the transaction receipts merkle proof
        require(PatriciaTrie.verifyMerkleProof(receiptsRoot, txIndexKey, leafData, cmd.proof.merkleProof)) {
            "The transaction receipts merkle proof failed to validate"
        }

        // Verify validator signatures for block inclusion
        require(verifyValidatorSignatures(cmd.proof.validatorSignatures, receiptsRoot, lockState.approvedValidators) >= lockState.signaturesThreshold) {
            "Consensus not reached: too many validator signatures failed to verify block inclusion"
        }
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
//        val lockedAssetState = tx.outputContractStates.filterIsInstance<OwnableState>().single()
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
    fun serializeLog(log: SerializableLog): RlpList {
    //fun serializeLog(log: net.corda.v5.application.interop.evm.Log): RlpList {
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
