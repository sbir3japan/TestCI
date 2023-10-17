package com.r3.corda.demo.interop.tokens.workflows.interop

import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.common.transaction.TransactionSignatureVerificationService
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.util.*

@InitiatingFlow(protocol = "lock-responder-sub-flow")
class LockFlow: FacadeDispatcherFlow(), LockFacade{

    @CordaInject
    lateinit var transactionSignatureVerificationService: TransactionSignatureVerificationService

    @CordaInject
    lateinit var digestService: DigestService

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun createLock(denomination: String, amount: BigDecimal, notaryKeys: ByteBuffer, draft: String): UUID {
        return UUID.randomUUID()
    }

    @Suspendable
    override fun unlock(reservationRef: UUID, proof: DigitalSignatureAndMetadata): BigDecimal {
//        log.info("Here is the secure hash" + proof.by.toString())
//        val x509publicKey = X509EncodedKeySpec(key.array())
//        val kf: KeyFactory = KeyFactory.getInstance("EC")
//        val publicKey = kf.generatePublic(x509publicKey)
//
//        try {
//            transactionSignatureVerificationService.verifySignature(proof.by, proof, publicKey)
//        } catch (e: Exception) {
//            log.error("Transaction id ${proof.by} doesn't match the proof $proof signed by" +
//                    " ${Base64.getEncoder().encodeToString(publicKey.encoded)}, reason: ${e.message}")
//            throw e
//        }
//        log.info("Transaction id ${proof.by} is matching the proof $proof signed by " +
//                Base64.getEncoder().encodeToString(publicKey.encoded)
//        )
        return BigDecimal.ONE
    }

    @Suspendable
    override fun sendProof(signableData: String, proof: DigitalSignatureAndMetadata, key: ByteBuffer): BigDecimal {
        val secureHash = digestService.parseSecureHash(signableData)
        val x509publicKey = X509EncodedKeySpec(key.array())
        val kf: KeyFactory = KeyFactory.getInstance("EC")
        val publicKey = kf.generatePublic(x509publicKey)
        try {
            transactionSignatureVerificationService.verifySignature(secureHash, proof, publicKey)
        } catch (e: Exception) {
            log.error("Transaction id $secureHash doesn't match the proof $proof signed by" +
                    " ${Base64.getEncoder().encodeToString(publicKey.encoded)}, reason: ${e.message}")
            throw e
        }
        log.info("Transaction id $secureHash is matching the proof $proof signed by " +
                Base64.getEncoder().encodeToString(publicKey.encoded)
        )
        return BigDecimal.ONE
    }
}