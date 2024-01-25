package net.corda.ledger.utxo.flow.impl.persistence

enum class LedgerPersistenceMetricOperationName {
    FetchFilteredTransactions,
    FindGroupParameters,
    FindSignedLedgerTransactionWithStatus,
    FindTransactionIdsAndStatuses,
    FindTransactionWithStatus,
    FindUnconsumedStatesByType,
    FindWithNamedQuery,
    FindMerkleProofs,
    PersistSignedGroupParametersIfDoNotExist,
    PersistTransaction,
    PersistTransactionIfDoesNotExist,
    ResolveStateRefs,
    UpdateTransactionStatus,
    PersistMerkleProofIfDoesNotExist
}
