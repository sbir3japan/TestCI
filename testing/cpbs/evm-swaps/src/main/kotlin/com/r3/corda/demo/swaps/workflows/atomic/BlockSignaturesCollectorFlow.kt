package com.r3.corda.demo.swaps.workflows.atomic

import com.r3.corda.demo.swaps.states.swap.LockState
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.time.Instant

class RequestParams(
    val blockNumber: BigInteger?,
    val blocking: Boolean?
)
class BlockSignaturesCollectorFlowInputs {
    val blockNumber: BigInteger? = null
    val blocking: Boolean? = null
    val transactionId: SecureHash? = null
}

@Suppress("unused")
@InitiatingFlow(protocol = "issue-currency-flow")
class BlockSignaturesCollectorFlow : ClientStartableFlow {

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
            val inputs = requestBody.getRequestBodyAs(jsonMarshallingService, BlockSignaturesCollectorFlowInputs::class.java)

            val signedTransaction = ledgerService.findSignedTransaction(inputs.transactionId!!)
                ?: throw IllegalArgumentException("Transaction not found for ID: ${inputs.transactionId}")

            val lockState = signedTransaction.outputStateAndRefs
                .mapNotNull { it.state as? LockState }
                .singleOrNull()
                ?: throw IllegalArgumentException("Transaction ${inputs.transactionId} does not have a lock state")

            val validators = lockState.approvedValidators.mapNotNull {
                memberLookup.lookup(it)?.ledgerKeys?.firstOrNull()
            }

            val sessions = validators.map { flowMessaging.initiateFlow(it) }

            val receivableSessions = mutableListOf<FlowSession>()
            for(session in sessions) {
                try {
                    session.send(RequestParams(inputs.blockNumber, inputs.blocking))
                    receivableSessions.add(session)
                } catch (e: Throwable) {
                    // NOTE: gather as many signatures as possible, ignoring single errors.
                    log.error("Error while sending response.\nError: $e")
                }
            }


        } catch (e: Exception) {
            log.error("Unexpected error while processing Issue Currency Flow ", e)
            throw e
        }

    }
}