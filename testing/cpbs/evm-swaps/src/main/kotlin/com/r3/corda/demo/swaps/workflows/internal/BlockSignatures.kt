package com.r3.corda.demo.swaps.workflows.internal

import java.math.BigInteger
import net.corda.v5.base.annotations.CordaSerializable
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id

@CordaSerializable
@Entity
class BlockSignatures (
    @Id
    @Column(name = "block_number")
    val blockNumber: BigInteger,

    @Id
    @Column(name = "signature")
    val signature: ByteArray,
)