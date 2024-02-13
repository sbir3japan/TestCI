package net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.common

import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.utxo.data.transaction.verifyFilteredTransactionAndSignatures
import net.corda.ledger.utxo.flow.impl.flows.backchain.InvalidBackchainException
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainResolutionFlow
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoLedgerTransactionFactory
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.NotarySignatureVerificationService
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransactionAndSignatures
import net.corda.v5.membership.GroupParametersLookup
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class AbstractReceiveTransactionFlow<T>(
    protected val session: FlowSession
) : SubFlow<T> {

    protected companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var utxoLedgerTransactionFactory: UtxoLedgerTransactionFactory

    @CordaInject
    lateinit var serializationService: SerializationService

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @CordaInject
    lateinit var groupParametersLookup: GroupParametersLookup

    @CordaInject
    lateinit var notarySignatureVerificationService: NotarySignatureVerificationService

    @CordaInject
    lateinit var ledgerPersistenceService: UtxoLedgerPersistenceService

    protected fun performBackchainResolutionOrFilteredTransactionVerification(
        transactionId: SecureHash,
        notaryName: MemberX500Name,
        transactionDependencies: Set<SecureHash>,
        filteredDependencies: List<UtxoFilteredTransactionAndSignatures>?
    ) {
        if (transactionDependencies.isNotEmpty()) {
            if (filteredDependencies.isNullOrEmpty()) {
                // If we have dependencies but no filtered dependencies then we need to perform backchain resolution
                try {
                    flowEngine.subFlow(TransactionBackchainResolutionFlow(transactionDependencies, session))
                } catch (e: InvalidBackchainException) {
                    val message = "Invalid transaction: $transactionId found during back-chain resolution."
                    log.warn(message, e)
                    session.send(Payload.Failure<List<DigitalSignatureAndMetadata>>(message))
                    throw e
                }
            } else {
                // If we have dependencies and filtered dependencies then we need to perform filtered transaction verification
                require(filteredDependencies.size == transactionDependencies.size) {
                    "The number of filtered transactions received didn't match the number of dependencies."
                }

                val groupParameters = groupParametersLookup.currentGroupParameters
                val notary =
                    requireNotNull(groupParameters.notaries.firstOrNull { it.name == notaryName }) {
                        "Notary from initial transaction \"$notaryName\" " +
                                "cannot be found in group parameter notaries."
                    }

                // Verify the received filtered transactions
                filteredDependencies.forEach {
                    it.verifyFilteredTransactionAndSignatures(notary, notarySignatureVerificationService)
                }

                // Persist the verified filtered transactions
                ledgerPersistenceService.persistFilteredTransactionsAndSignatures(filteredDependencies)
            }
        }
    }
}