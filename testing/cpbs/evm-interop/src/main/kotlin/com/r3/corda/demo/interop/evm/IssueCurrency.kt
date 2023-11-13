package com.r3.corda.demo.interop.evm

import com.r3.corda.demo.interop.evm.state.FungibleTokenState
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.rmi.MarshalException


class IssueCurrencyInputs {
    val symbol: String? = null
    val amount: Int? = null
}

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
                balances = mapOf(key to inputs.amount!!.toLong()),
                participants = listOf(key)
            )

            val txBuilder = ledgerService.createTransactionBuilder()
                .setNotary(notaryLookup.notaryServices.single().name)
                .addOutputState(state)
            val signedTransaction = txBuilder.toSignedTransaction()
            val notary = notaryLookup.notaryServices.single()
            val names = memberLookup.lookup().filter {
                it.memberProvidedContext["corda.notary.service.name"] != notary.name.toString()
            }.map {
                it.name
            }

            // Broadcast to everyone
            val sessions = names.map { flowMessaging.initiateFlow(it) }
            val finalizedSignedTransaction = ledgerService.finalize(signedTransaction, sessions)

            return finalizedSignedTransaction.transaction.id.toString()

        } catch (e: Exception) {
            log.error("Unexpected error while processing Issue Currency Flow ", e)
            throw e;
        }

    }
}