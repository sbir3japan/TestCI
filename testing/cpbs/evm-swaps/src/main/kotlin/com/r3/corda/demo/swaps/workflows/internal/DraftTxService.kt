package com.r3.corda.demo.swaps.workflows.internal

import com.r3.corda.demo.swaps.BlockSignatures
import com.r3.corda.demo.swaps.DigitalSignatureList
import com.r3.corda.demo.swaps.EvmSignatures
import com.r3.corda.demo.swaps.TransactionBytes
import java.math.BigInteger
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction

/**
 * Simple [CordaService] used to store and retrieve swap transaction information
 * TODO: Current implementation is suitable only for testing. A more robust approach is needed
 */
//object DraftTxService : SingletonSerializeAsToken {
class DraftTxService(
    val persistenceService: PersistenceService,
    val serializationService: SerializationService
){
    @Suspendable
    fun saveBlockSignatures(blockNumber: BigInteger, signatures: List<DigitalSignature.WithKeyId>) {
        persistenceService.merge(
            BlockSignatures(
                blockNumber = blockNumber,
                signature = serializationService.serialize(DigitalSignatureList(signatures)).bytes
            )
        )
    }

    @Suspendable
    fun saveNotarizationProof(transactionId: SecureHash, signature: ByteArray) {
        persistenceService.persist(
            EvmSignatures(
                transactionId = transactionId.toString(),
                signature = signature
            )
        )
    }

    @Suspendable
    fun blockSignatures(blockNumber: BigInteger): List<DigitalSignature.WithKeyId> {

        val blockSignatures = persistenceService.find(
            BlockSignatures::class.java,
            listOf(blockNumber)
        ).first()

        val signaturesList = serializationService.deserialize(blockSignatures.signature, DigitalSignatureList::class.java)

        return signaturesList.signatures
    }

    @Suspendable
    fun notarizationProofs(transactionId: SecureHash): List<ByteArray> {
        return persistenceService.find(
            EvmSignatures::class.java,
            listOf(transactionId)
        ).map { it.signature }
    }

    @Suspendable
    fun saveDraftTx(transaction: UtxoSignedTransaction) {
        persistenceService.persist(
            TransactionBytes(
                transaction.id.toString(),
                serializationService.serialize(transaction).bytes
            )
        )
    }

    @Suspendable
    fun getDraftTx(transactionId: SecureHash): UtxoSignedTransaction? {
        return persistenceService.find(
            TransactionBytes::class.java,
            listOf(transactionId)
        ).singleOrNull()?.let {
            return serializationService.deserialize(it.serializedTransaction, UtxoSignedTransaction::class.java)
        }
    }

    @Suspendable
    fun deleteDraftTx(id: SecureHash) {
        persistenceService.find(
            TransactionBytes::class.java,
            listOf(id)
        ).singleOrNull()?.let {
            persistenceService.remove(it)
        }
    }
}
