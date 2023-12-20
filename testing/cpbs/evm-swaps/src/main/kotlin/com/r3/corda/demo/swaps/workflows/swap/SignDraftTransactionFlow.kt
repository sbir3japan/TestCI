//package com.r3.corda.demo.swaps.workflows.swap
//
//import com.r3.corda.demo.swaps.workflows.atomic.Keccak256SignatureSpec
//import com.r3.corda.demo.swaps.workflows.internal.DraftTxService
//import net.corda.v5.application.crypto.DigitalSignatureMetadata
//import net.corda.v5.application.flows.ClientRequestBody
//import net.corda.v5.application.flows.ClientStartableFlow
//import net.corda.v5.application.flows.CordaInject
//import net.corda.v5.application.flows.FlowEngine
//import net.corda.v5.application.flows.InitiatedBy
//import net.corda.v5.application.flows.ResponderFlow
//import net.corda.v5.application.marshalling.JsonMarshallingService
//import net.corda.v5.application.membership.MemberLookup
//import net.corda.v5.application.messaging.FlowMessaging
//import net.corda.v5.application.messaging.FlowSession
//import net.corda.v5.base.annotations.Suspendable
//import net.corda.v5.crypto.SecureHash
//import net.corda.v5.ledger.common.NotaryLookup
//import net.corda.v5.ledger.utxo.UtxoLedgerService
//import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
//import org.slf4j.LoggerFactory
//import java.time.Instant
//
//
//class SignedDraftTransactionFlowInputs {
//    val transactionId: UtxoLedgerTransaction? = null
//}
//
//@Suppress("unused")
//@InitiatedBy(protocol = "sign-draft-transaction-by-id-flow")
//class SignDraftTransactionFlow : ResponderFlow {
//
//    private companion object {
//        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
//    }
//
//    @CordaInject
//    lateinit var jsonMarshallingService: JsonMarshallingService
//
//    @CordaInject
//    lateinit var memberLookup: MemberLookup
//
//    @CordaInject
//    lateinit var ledgerService: UtxoLedgerService
//
//    @CordaInject
//    lateinit var notaryLookup: NotaryLookup
//
//    @CordaInject
//    lateinit var flowMessaging: FlowMessaging
//
//    @CordaInject
//    lateinit var flowEngine: FlowEngine
//
//    @Suspendable
//    override fun call(session: FlowSession) {
//        try {
//
//
//            // retrieve the inputs
//
//
//
//
//            // Sign the draft transaction
////            val signatureMetadata = S      ignatureMetadata(
////                serviceHub.myInfo.platformVersion,
////                Crypto.findSignatureScheme(ourIdentity.owningKey).schemeNumberID
////            )
////            val signableData = SignableData(draftTx.id, signatureMetadata)
////            val sig = serviceHub.keyManagementService.sign(signableData, ourIdentity.owningKey)
////
////            val sessions = (draftTx.outputsOfType<LockState>().single().participants - ourIdentity).map { initiateFlow(it) }
////            val stx = SignedTransaction(draftTx, listOf(sig))
////            return subFlow(FinalityFlow(stx, sessions))v
//
//            DigitalSignatureMetadata(
//                Instant.now(),
//                Keccak256SignatureSpec
//
//            )
//        } catch (e: Exception) {
//            log.error("Unexpected error while processing Issue Currency Flow ", e)
//            throw e
//        }
//
//    }
//}
