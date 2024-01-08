package com.r3.corda.demo.swaps

import java.math.BigInteger
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.DigitalSignature
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import javax.persistence.*

@CordaSerializable
data class DigitalSignatureList(val signatures: List<DigitalSignature.WithKeyId>)

@CordaSerializable
@Entity(name = "block_signatures")
class BlockSignatures (
    @Id
    @Column(name = "block_number")
    val blockNumber: BigInteger,

    @Column(name = "signature")
    val signature: ByteArray,
)
