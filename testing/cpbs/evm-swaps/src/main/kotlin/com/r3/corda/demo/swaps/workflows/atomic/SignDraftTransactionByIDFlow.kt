package com.r3.corda.demo.swaps.workflows.atomic

import com.r3.corda.demo.swaps.TransactionBytes
import com.r3.corda.demo.swaps.states.swap.LockState
import net.corda.v5.application.flows.*
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import org.slf4j.LoggerFactory
import java.awt.color.ICC_ColorSpace


data class SignDraftTransactionByIdArgs(val transactionId: SecureHash)

/**
 * Initiating flow which takes a draft transaction and attempts to sign and notarize it.
 *
 * @param transactionId the Draft Transaction ID to sign
 */
@Suppress("unused")
@InitiatingFlow(protocol = "sign-draft-transaction-by-id-flow")
class SignDraftTransactionByIdFlow : ClientStartableFlow {

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

    @CordaInject
    lateinit var persistenceService: PersistenceService

    @CordaInject
    lateinit var serializationService: SerializationService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        try {
            val (transactionId) = requestBody.getRequestBodyAs(
                jsonMarshallingService,
                SignDraftTransactionByIdArgs::class.java
            )

            val transactionData = persistenceService.find(
                TransactionBytes::class.java,
                listOf(transactionId.toString())
            ).singleOrNull() ?: throw IllegalArgumentException("No transaction found by the id $transactionId")

            val signedTransaction = serializationService.deserialize(
                transactionData.serializedTransaction,
                UtxoSignedTransaction::class.java
            )

            @Suppress("UNCHECKED_CAST") val lockState =
                signedTransaction.outputStateAndRefs.singleOrNull { it.state.contractState is LockState } as? StateAndRef<LockState>
                    ?: throw IllegalArgumentException("Transaction $transactionId does not have a lock state")

            val ourIdentityKey = memberLookup.myInfo().ledgerKeys.first()
            val sessions = lockState.state.contractState.participants
                .asSequence()
                .mapNotNull(memberLookup::lookup)
                .filter { !it.ledgerKeys.contains(ourIdentityKey) }
                .map { flowMessaging.initiateFlow(it.name) }
                .toList()

            ledgerService.finalize(signedTransaction, sessions)

            return signedTransaction.id.toString()

        } catch (e: Exception) {
            throw CordaRuntimeException("Failed to sign transaction by ID", e)
        }
    }
}

/**
 * Responder flow which receives a finalized transaction
 */
@Suppress("unused")
@InitiatedBy(protocol = "sign-draft-transaction-by-id-flow")
class SignDraftTransactionByIdResponder : ResponderFlow {

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

    @CordaInject
    lateinit var persistenceService: PersistenceService

    @CordaInject
    lateinit var serializationService: SerializationService

    @Suspendable
    override fun call(session: FlowSession) {
        try {

            ledgerService.receiveFinality(session) { tx ->
                // TODO: add required checks

                tx.signatories

                // TODO: remove draft
//                val transactionData = persistenceService.find(
//                    TransactionBytes::class.java,
//                    listOf(tx.id)
//                ).singleOrNull()
//
//                if (transactionData != null) {
//                    persistenceService.remove(transactionData)
//                } else {
//                    throw IllegalArgumentException("Trying to sign a non existing draft transaction")
//                }
            }

        } catch (e: Exception) {
            throw CordaRuntimeException("Failed to receive finality", e)
        }
    }
}
