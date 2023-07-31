package net.corda.ledger.utxo.datamodel

object UtxoLedgerEntities {
    val classes = setOf(
        UtxoGroupParametersEntity::class.java,
        UtxoTransactionComponentEntity::class.java,
        UtxoTransactionEntity::class.java,
        UtxoTransactionOutputEntity::class.java,
        UtxoTransactionSignatureEntity::class.java,
        UtxoTransactionSourceEntity::class.java,
        UtxoTransactionStatusEntity::class.java,
        UtxoVisibleTransactionStateEntity::class.java
    )
}
