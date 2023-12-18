package com.r3.corda.demo.swaps.workflows.internal

import java.math.BigInteger
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.serialization.SingletonSerializeAsToken

/**
 * Simple [CordaService] used to store and retrieve swap transaction information
 * TODO: Current implementation is suitable only for testing. A more robust approach is needed
 */
object DraftTxService : SingletonSerializeAsToken {

    @CordaInject
    lateinit var persistenceService: PersistenceService

    @CordaInject
    lateinit var serializationService: SerializationService

    fun saveBlockSignature(blockNumber: BigInteger, signature: DigitalSignature.WithKeyId) {
        persistenceService.merge(
            BlockSignatures(
                blockNumber = blockNumber,
                signature = serializationService.serialize(signature).bytes
            )
        )
    }

    fun saveNotarizationProof(transactionId: SecureHash, signature: ByteArray) {
        persistenceService.persist(
            EvmSignatures(
                transactionId = transactionId.toString(),
                signature = signature
            )
        )
    }

    fun blockSignatures(blockNumber: BigInteger): List<DigitalSignature.WithKeyId> {
        return persistenceService.find(
            BlockSignatures::class.java,
            listOf(blockNumber)
        ).map {
            serializationService.deserialize(
                it.signature,
                DigitalSignature.WithKeyId::class.java
            )
        }
    }

    fun notarizationProofs(transactionId: SecureHash): List<ByteArray> {
        return persistenceService.find(
            EvmSignatures::class.java,
            listOf(transactionId)
        ).map { it.signature }
    }

    fun saveDraftTx(tx: UtxoSignedTransaction) {
        persistenceService.persist(
            DraftTransaction(
                transactionId = tx.id.toString(),
                transaction = serializationService.serialize(tx).bytes
            )
        )
    }

    fun getDraftTx(id: SecureHash): UtxoSignedTransaction? {
        return persistenceService.find(
            DraftTransaction::class.java,
            listOf(id)
        ).singleOrNull()?.let {
            return serializationService.deserialize(it.transaction, UtxoSignedTransaction::class.java)
        }
    }

    fun deleteDraftTx(id: SecureHash) {
        persistenceService.find(
            DraftTransaction::class.java,
            listOf(id)
        ).singleOrNull()?.let {
            persistenceService.remove(it)
        }
    }
}
