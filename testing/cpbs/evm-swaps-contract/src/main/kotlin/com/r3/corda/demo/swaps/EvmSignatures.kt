package com.r3.corda.demo.swaps

import net.corda.v5.base.annotations.CordaSerializable
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id

@CordaSerializable
@Entity(name = "evm_signatures")
class EvmSignatures (
    @Id
    @Column(name = "transaction_id")
    val transactionId: String,

    @Column(name = "signature")
    val signature: ByteArray
)
