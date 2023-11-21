package com.r3.corda.demo.interop.evm

import com.corda.evm.contracts.TokenCommand
import com.corda.evm.states.FungibleTokenState
import net.corda.v5.application.flows.*
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import org.slf4j.LoggerFactory
import java.time.Instant

class IssueCurrencyInputs {
    val symbol: String? = null
    val amount: Int? = null
}

//@InitiatingFlow(protocol = "issue-currency-flow")
@Suppress("unused")
@InitiatingFlow(protocol = "issue-currency-flow")
class IssueCurrency : ClientStartableFlow {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }


    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var flowEngine: FlowEngine


    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        try {
            val inputs = requestBody.getRequestBodyAs(jsonMarshallingService, IssueCurrencyInputs::class.java)

            val myInfo = memberLookup.myInfo()

            val key = myInfo.ledgerKeys.first()

            val state = FungibleTokenState(
                valuation = 1,
                maintainer = key,
                fractionDigits = 0,
                symbol = inputs.symbol!!,
                balances = inputs.amount!!.toLong(),
                participants = listOf(key)
            )

            val txBuilder = ledgerService.createTransactionBuilder()
                .setNotary(notaryLookup.notaryServices.single().name)
                .addOutputState(state)
                .setTimeWindowUntil(Instant.now().plusSeconds(300000))
                .addSignatories(key)
                .addCommand(TokenCommand.Issue)

            log.info("Transaction builder: $txBuilder")

            val session = flowMessaging.initiateFlow(myInfo.name)

            val output = ledgerService.finalize(
                txBuilder.toSignedTransaction(),
                listOf(session)
            )

            return output.transaction.id.toString()





        } catch (e: Exception) {
            log.error("Unexpected error while processing Issue Currency Flow ", e)
            throw e;
        }

    }
}




@InitiatedBy(protocol = "issue-currency-flow")
class FinalizeIssueCurrencySubFlow : ResponderFlow {

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @Suspendable
    override fun call(session: FlowSession) {
        // Receive, verify, validate, sign and record the transaction sent from the initiator
        utxoLedgerService.receiveFinality(session) {

            /*
             * [receiveFinality] will automatically verify the transaction and its signatures before signing it.
             * However, just because a transaction is contractually valid doesn't mean we necessarily want to sign.
             * What if we don't want to deal with the counterparty in question, or the value is too high,
             * or we're not happy with the transaction's structure? [UtxoTransactionValidator] (the lambda created
             * here) allows us to define the additional checks. If any of these conditions are not met,
             * we will not sign the transaction - even if the transaction and its signatures are contractually valid.
             */
        }
    }
}
//@InitiatedBy(protocol = "issue-currency-flow")
//class FinalizeIssueCurrencySubFlow(private val signedTransaction: UtxoSignedTransaction, private val otherMembers: List<MemberX500Name>): SubFlow<String> {
//
//    private companion object {
//        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
//    }
//
//    // Injects the UtxoLedgerService to enable the flow to make use of the Ledger API.
//    @CordaInject
//    lateinit var ledgerService: UtxoLedgerService
//
//    @CordaInject
//    lateinit var flowMessaging: FlowMessaging
//
//    @Suspendable
//    override fun call(): String {
//
//        log.info("FinalizeKudosFlow.call() called")
//        val sessions = otherMembers.map {
//            flowMessaging.initiateFlow(it)
//        }
//
//        return try {
//            val finalizedSignedTransaction = ledgerService.finalize(
//                signedTransaction,
//                sessions
//            )
//            finalizedSignedTransaction.transaction.id.toString().also {
//                log.info("Success! Response: $it")
//            }
//        }
//        // Soft fails the flow and returns the error message without throwing a flow exception.
//        catch (e: Exception) {
//            log.warn("Finality failed", e)
//            "Finality failed, ${e.message}"
//        }
//    }
//}


//@InitiatedBy(protocol = "issue-currency-flow")
//// Responder flows must inherit from ResponderFlow
//class IssueCurrencyResponderFlow(sessions: FlowSession): ResponderFlow {
//
//    // It is useful to be able to log messages from the flows for debugging.
//    private companion object {
//        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
//    }
//
//    // MemberLookup provides a service for looking up information about members of the Virtual Network which
//    // this CorDapp is operating in.
//    @CordaInject
//    lateinit var memberLookup: MemberLookup
//
//
//    // Responder flows are invoked when an initiating flow makes a call via a session set up with the Virtual
//    // node hosting the Responder flow. When a responder flow is invoked, its call() method is called.
//    // call() methods must be marked as @Suspendable, this allows Corda to pause mid-execution to wait
//    // for a response from the other flows and services/
//    // The Call method has the flow session passed in as a parameter by Corda so the session is available to
//    // responder flow code, you don't need to inject the FlowMessaging service.
//    @Suspendable
//    override fun call() {
//       log.info("Started Flow Call")
//
//        val finalizedSignedTransaction = ledgerService.finalize(signedTransaction, sessions)
//
//        return finalizedSignedTransaction.transaction.id.toString()
//
//
//    }
//}