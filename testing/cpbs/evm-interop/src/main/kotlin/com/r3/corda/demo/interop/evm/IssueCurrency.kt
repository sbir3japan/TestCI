package com.r3.corda.demo.interop.evm

import com.corda.evm.contracts.TokenCommand
import com.corda.evm.states.FungibleTokenState
import net.corda.v5.application.flows.*
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.time.Instant

class IssueCurrencyInputs {
    val symbol: String? = null
    val amount: Int? = null
}

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

    /**
     * This function builds issues a currency on Corda
     */
    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        try {
            // Get any of the relevant details from the request here
            val inputs = requestBody.getRequestBodyAs(jsonMarshallingService, IssueCurrencyInputs::class.java)

            // Save the users key
            val key = memberLookup.myInfo().ledgerKeys.first()

            // Get the notary name
            val notaryName = notaryLookup.notaryServices.single().name

            // Get the key of the other participant
            val participants = memberLookup.lookup().filter {
                it.memberProvidedContext["corda.notary.service.name"] != notaryName.toString()
            }.map {
                it.ledgerKeys.first()
            }

            // Filter out the other participants key
            val otherKey = participants.single { it != key }

            // Create the balance mapping
            val balanceMapping = mapOf(key to inputs.amount!!.toLong(),otherKey to 0L)

            // Create the state
            val state = FungibleTokenState(
                valuation = 1,
                maintainer = key,
                fractionDigits = 0,
                symbol = inputs.symbol!!,
                balances = balanceMapping,
                participants = participants
            )

            // Create the transaction builder
            val txBuilder = ledgerService.createTransactionBuilder()
                .setNotary(notaryName)
                .addOutputState(state)
                .setTimeWindowUntil(Instant.now().plusSeconds(300000))
                .addSignatories(key)
                .addCommand(TokenCommand.Issue)


            // Get the sessions for the other participants
            val sessions = memberLookup.lookup().filter {
                it.memberProvidedContext["corda.notary.service.name"] != notaryName.toString() && it.ledgerKeys.first() != key
            }.map {
                flowMessaging.initiateFlow(it.name)
            }

            // Finalize the transaction
            val output = ledgerService.finalize(
                txBuilder.toSignedTransaction(),
                sessions
            )

            // Returns the transaction id
            return output.transaction.id.toString()

        } catch (e: Exception) {
            log.error("Unexpected error while processing Issue Currency Flow ", e)
            throw e
        }

    }
}



@Suppress("unused")
@InitiatedBy(protocol = "issue-currency-flow")
class FinalizeIssueCurrencySubFlow : ResponderFlow {

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @Suspendable
    override fun call(session: FlowSession) {
        // Receive, verify, validate, sign and record the transaction sent from the initiator
        utxoLedgerService.receiveFinality(session) {

        }
    }
}