package com.r3.corda.demo.swaps

import java.math.BigInteger
import net.corda.v5.base.annotations.CordaSerializable
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id

@CordaSerializable
@Entity(name = "block_signatures")
class BlockSignatures (
    @Id
    @Column(name = "block_number")
    val blockNumber: BigInteger,

    @Column(name = "signature")
    val signature: ByteArray,
)
