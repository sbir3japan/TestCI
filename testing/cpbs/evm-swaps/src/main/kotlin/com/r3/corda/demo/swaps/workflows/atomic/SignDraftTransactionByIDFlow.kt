//package com.r3.corda.demo.swaps.workflows.atomic
//
//import com.r3.corda.demo.swaps.workflows.internal.DraftTxService
//import net.corda.v5.application.flows.ClientRequestBody
//import net.corda.v5.application.flows.ClientStartableFlow
//import net.corda.v5.application.flows.CordaInject
//import net.corda.v5.application.flows.FlowEngine
//import net.corda.v5.application.flows.InitiatingFlow
//import net.corda.v5.application.marshalling.JsonMarshallingService
//import net.corda.v5.application.membership.MemberLookup
//import net.corda.v5.application.messaging.FlowMessaging
//import net.corda.v5.base.annotations.Suspendable
//import net.corda.v5.crypto.SecureHash
//import net.corda.v5.ledger.common.NotaryLookup
//import net.corda.v5.ledger.utxo.UtxoLedgerService
//import org.slf4j.LoggerFactory
//
//
//class SignedDraftTransactionFlowByIdInputs {
//    val transactionId: SecureHash? = null
//}
//
//@Suppress("unused")
//@InitiatingFlow(protocol = "sign-draft-transaction-by-id-flow")
//class SignedDraftTransactionByIdFlow : ClientStartableFlow {
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
//    override fun call(requestBody: ClientRequestBody): String {
//        try {
//            val inputs = requestBody.getRequestBodyAs(jsonMarshallingService, SignedDraftTransactionFlowByIdInputs::class.java)
//            DraftTxService.getDraftTx(inputs.transactionId!!)
//
//        } catch (e: Exception) {
//            log.error("Unexpected error while processing Issue Currency Flow ", e)
//            throw e
//        }
//
//    }
//}
