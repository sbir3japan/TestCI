package com.r3.corda.demo.swaps

import net.corda.v5.base.annotations.CordaSerializable
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id

@CordaSerializable
@Entity(name = "drafttx")
data class TransactionBytes(
    @Id
    @Column(name = "id")
    val transactionId: String,
    @Column(name = "tx")
    val serializedTransaction: ByteArray
) {

    constructor() : this("SHA-256D:5df6e0e2761359d30a8275058e299fcc0381534545f55cf43e41983f5d4c9456", ByteArray(0))

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TransactionBytes) return false

        if (transactionId != other.transactionId) return false
        if (!serializedTransaction.contentEquals(other.serializedTransaction)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = transactionId.hashCode()
        result = 31 * result + serializedTransaction.contentHashCode()
        return result
    }
}
