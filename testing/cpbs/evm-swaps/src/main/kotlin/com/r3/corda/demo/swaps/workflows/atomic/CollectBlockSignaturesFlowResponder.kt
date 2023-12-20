package com.r3.corda.demo.swaps.workflows.atomic

import net.corda.v5.application.flows.*
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.math.BigInteger


class CollectBlockSignaturesParams(
    val recipient: MemberX500Name,
    val blockNumber: BigInteger?,
    val blocking: Boolean?
)

@Suppress("unused")
@InitiatedBy(protocol = "collect-block-signatures-flow")
@InitiatingFlow(protocol = "collect-block-signatures-responder-flow")
class CollectBlockSignaturesFlowResponder : ResponderFlow {

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
    override fun call(session: FlowSession) {
        try {


            // Get any of the relevant details from the request here
            val requestParams = session.receive(RequestParams::class.java)
            flowMessaging.initiateFlow(memberLookup.myInfo().name)

            session.send(
                CollectBlockSignaturesParams(
                    session.counterparty,
                    requestParams.blockNumber,
                    requestParams.blocking
                )
            )

            if (requestParams.blocking == true) {
                session.send(true)

            }


        } catch (e: Exception) {
            log.error("Unexpected error while processing CollectBlockSignaturesFlowResponder ", e)
            throw e
        }

    }
}