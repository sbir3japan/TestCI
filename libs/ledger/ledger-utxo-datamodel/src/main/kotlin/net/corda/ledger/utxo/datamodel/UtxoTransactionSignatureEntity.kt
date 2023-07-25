package net.corda.ledger.utxo.datamodel

import java.io.Serializable
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.IdClass
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table

@Entity
@Table(name = "utxo_transaction_signature")
@IdClass(UtxoTransactionSignatureEntityId::class)
class UtxoTransactionSignatureEntity(
    @Id
    @ManyToOne
    @JoinColumn(name = "transaction_id", nullable = false, updatable = false)
    val transaction: UtxoTransactionEntity,

    @Id
    @Column(name = "signature_idx", nullable = false)
    val index: Int,

    @Column(name = "signature", nullable = false)
    val signature: ByteArray,

    @Column(name = "pub_key_hash", nullable = false)
    val publicKeyHash: String,

    @Column(name = "created", nullable = false)
    val created: Instant
) : Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UtxoTransactionSignatureEntity

        if (transaction != other.transaction) return false
        if (index != other.index) return false

        return true
    }

    override fun hashCode(): Int {
        var result = transaction.hashCode()
        result = 31 * result + index
        return result
    }

    companion object {
        private const val serialVersionUID: Long = -7652753106360934329L
    }
}

@Embeddable
class UtxoTransactionSignatureEntityId(
    var transaction: UtxoTransactionEntity,
    var index: Int
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 7444354660976338805L
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UtxoTransactionSignatureEntityId

        if (transaction != other.transaction) return false
        if (index != other.index) return false

        return true
    }

    override fun hashCode(): Int {
        var result = transaction.hashCode()
        result = 31 * result + index
        return result
    }
}