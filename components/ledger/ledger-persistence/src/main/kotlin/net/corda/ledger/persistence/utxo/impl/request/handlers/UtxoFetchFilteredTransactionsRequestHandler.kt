package net.corda.ledger.persistence.utxo.impl.request.handlers

import net.corda.crypto.core.SecureHashImpl
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.FetchFilteredTransactions
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.ledger.persistence.utxo.UtxoOutputRecordFactory
import net.corda.ledger.persistence.utxo.UtxoPersistenceService
import net.corda.messaging.api.records.Record
import net.corda.v5.ledger.utxo.StateRef

class UtxoFetchFilteredTransactionsRequestHandler(
    private val fetchFilteredTransactions: FetchFilteredTransactions,
    private val externalEventContext: ExternalEventContext,
    private val persistenceService: UtxoPersistenceService,
    private val utxoOutputRecordFactory: UtxoOutputRecordFactory
) : RequestHandler {
    override fun execute(): List<Record<*, *>> {
        val filteredTransactionFetchResults = persistenceService.fetchFilteredTransactions(
            fetchFilteredTransactions.stateRefs.map {
                StateRef(
                    SecureHashImpl(it.transactionId.algorithm, it.transactionId.bytes.array()),
                    it.index
                )
            }
        )
        return listOf(
            utxoOutputRecordFactory.getFetchFilteredTransactionsSuccessRecord(
                filteredTransactionFetchResults,
                externalEventContext
            )
        )
    }
}