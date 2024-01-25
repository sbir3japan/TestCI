package net.corda.ledger.persistence.utxo.impl

import com.fasterxml.jackson.core.JsonProcessingException
import net.corda.crypto.core.parseSecureHash
import net.corda.data.membership.SignedGroupParameters
import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.data.transaction.filtered.FilteredComponentGroup
import net.corda.ledger.common.data.transaction.filtered.FilteredTransaction
import net.corda.ledger.common.data.transaction.filtered.factory.FilteredTransactionFactory
import net.corda.ledger.common.data.transaction.TransactionStatus.VERIFIED
import net.corda.ledger.common.data.transaction.filtered.ComponentGroupFilterParameters
import net.corda.ledger.persistence.common.InconsistentLedgerStateException
import net.corda.ledger.persistence.json.ContractStateVaultJsonFactoryRegistry
import net.corda.ledger.persistence.json.DefaultContractStateVaultJsonFactory
import net.corda.ledger.persistence.utxo.CustomRepresentation
import net.corda.ledger.persistence.utxo.UtxoPersistenceService
import net.corda.ledger.persistence.utxo.UtxoRepository
import net.corda.ledger.persistence.utxo.UtxoTransactionReader
import net.corda.ledger.utxo.data.transaction.MerkleProofDto
import net.corda.ledger.utxo.data.transaction.SignedLedgerTransactionContainer
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup.METADATA
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup.NOTARY
import net.corda.ledger.utxo.data.transaction.UtxoOutputInfoComponent
import net.corda.ledger.utxo.data.transaction.UtxoVisibleTransactionOutputDto
import net.corda.ledger.utxo.data.transaction.WrappedUtxoWireTransaction
import net.corda.libs.packaging.hash
import net.corda.orm.utils.transaction
import net.corda.utilities.serialization.deserialize
import net.corda.utilities.time.Clock
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.CordaPackageSummary
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.observer.UtxoToken
import net.corda.v5.ledger.utxo.query.json.ContractStateVaultJsonFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

@Suppress("LongParameterList")
class UtxoPersistenceServiceImpl(
    private val entityManagerFactory: EntityManagerFactory,
    private val repository: UtxoRepository,
    private val serializationService: SerializationService,
    private val sandboxDigestService: DigestService,
    private val factoryStorage: ContractStateVaultJsonFactoryRegistry,
    private val defaultContractStateVaultJsonFactory: DefaultContractStateVaultJsonFactory,
    private val jsonMarshallingService: JsonMarshallingService,
    private val utcClock: Clock
) : UtxoPersistenceService {

    private companion object {
        val log: Logger = LoggerFactory.getLogger(UtxoPersistenceServiceImpl::class.java)
    }

    override fun findSignedTransaction(
        id: String,
        transactionStatus: TransactionStatus
    ): Pair<SignedTransactionContainer?, String?> {
        return entityManagerFactory.transaction { em ->
            val status = repository.findTransactionStatus(em, id)
            if (status == transactionStatus.value) {
                repository.findTransaction(em, id)
                    ?: throw InconsistentLedgerStateException("Transaction $id in status $status has disappeared from the database")
            } else {
                null
            } to status
        }
    }

    override fun fetchFilteredTransactions(
        stateRefs: List<StateRef>,
    ): Map<SecureHash, Pair<FilteredTransaction?, List<DigitalSignatureAndMetadata>>> {

        val txIdToIndexesMap = stateRefs.groupBy { it.transactionId }
            .mapValues { (_, stateRefs) -> stateRefs.map { stateRef -> stateRef.index } }
        val txIdToFilteredTxAndSignature: MutableMap<SecureHash, Pair<FilteredTransaction?, List<DigitalSignatureAndMetadata>>> =
            stateRefs
                .groupBy { it.transactionId }
                .mapValues { (_, _) -> null to emptyList<DigitalSignatureAndMetadata>() }.toMutableMap()

        txIdToIndexesMap.keys.forEach { transactionId ->

            require(txIdToFilteredTxAndSignature.containsKey(transactionId)) { "transaction Id $transactionId is not found." }

            val signedTransactionContainer = findSignedTransaction(transactionId.toString(), VERIFIED).first
            val wireTransaction = signedTransactionContainer?.wireTransaction
            val signatures = signedTransactionContainer?.signatures ?: emptyList()
            val indexesOfTxId = requireNotNull(txIdToIndexesMap[transactionId])

            if (wireTransaction != null) {

//                val dependencyNotaryName = serializationService.deserialize(
//                    wireTransaction.componentGroupLists[NOTARY.ordinal].first(),
//                    MemberX500Name::class.java
//                )

//                require(notaryName == dependencyNotaryName) {
//                    "Notary name of filtered transaction \"${dependencyNotaryName}\" doesn't match with " +
//                            "notary service of current transaction \"${notaryName}\""
//                }

                // verify the signature in flow side when packing to filtered tx & signatures obj
                // send something to say the signature is not valid.
//                if (signatures.isNotEmpty()) {
//                    notarySignatureVerificationService.verifyNotarySignatures(
//                        wireTransaction,
//                        notaryKey,
//                        signatures.toMutableList(),
//                        mutableMapOf()
//                    )
//                }

                // filtering notary signatures should be done in flow worker
//                val notaryKeyIds = if (notaryKey is CompositeKey) {
//                    notaryKey.leafKeys.toSet()
//                } else {
//                    setOf(notaryKey.fullIdHash())
//                }
//                val notarySignature = signatures.first { notaryKeyIds.contains(it.by) }

                // filter wire transaction that is equivalent to:
                //            var filteredTxBuilder = filteredTransactionBuilder
                //                    .withTimeWindow()
                //                    .withOutputStates(indexesOfTxId)
                //                    .withNotary()
                val filteredTransaction = filteredTransactionFactory.create(
                    wireTransaction,
                    listOf(
                        ComponentGroupFilterParameters.AuditProof(
                            METADATA.ordinal,
                            TransactionMetadata::class.java,
                            ComponentGroupFilterParameters.AuditProof.AuditProofPredicate.Content { true }
                        ),
                        ComponentGroupFilterParameters.AuditProof(
                            NOTARY.ordinal,
                            Any::class.java,
                            ComponentGroupFilterParameters.AuditProof.AuditProofPredicate.Content { true }
                        ),
                        ComponentGroupFilterParameters.AuditProof(
                            UtxoComponentGroup.OUTPUTS_INFO.ordinal,
                            UtxoOutputInfoComponent::class.java,
                            ComponentGroupFilterParameters.AuditProof.AuditProofPredicate.Index(indexesOfTxId)
                        ),
                        ComponentGroupFilterParameters.AuditProof(
                            UtxoComponentGroup.OUTPUTS.ordinal,
                            UtxoOutputInfoComponent::class.java,
                            ComponentGroupFilterParameters.AuditProof.AuditProofPredicate.Index(indexesOfTxId)
                        )
                    )
                )
                txIdToFilteredTxAndSignature[transactionId] = filteredTransaction to signatures
            }
        }
//        val transactionIdsToFind = txIdToFilteredTransaction.filter { it.value.toList().contains(null) }.keys.map { it.toString() }
//        val filteredTransactions = findFilteredTransactions(transactionIdsToFind)
        return txIdToFilteredTxAndSignature
//        return txIdToFilteredTransaction + filteredTransactions
    }

    override fun findTransactionIdsAndStatuses(
        transactionIds: List<String>
    ): Map<SecureHash, String> {
        return entityManagerFactory.transaction { em ->
            repository.findTransactionIdsAndStatuses(em, transactionIds)
        }
    }

    override fun findSignedLedgerTransaction(
        id: String,
        transactionStatus: TransactionStatus
    ): Pair<SignedLedgerTransactionContainer?, String?> {
        return entityManagerFactory.transaction { em ->
            val status = repository.findTransactionStatus(em, id)
            if (status == transactionStatus.value) {
                val (transaction, signatures) = repository.findTransaction(em, id)
                    ?.let { WrappedUtxoWireTransaction(it.wireTransaction, serializationService) to it.signatures }
                    ?: throw InconsistentLedgerStateException("Transaction $id in status $status has disappeared from the database")

                val allStateRefs = (transaction.inputStateRefs + transaction.referenceStateRefs).distinct()

                // Note: calling the `resolveStateRefs` function would result in a new connection being established,
                // so we call the repository directly instead
                val stateRefsToStateAndRefs = repository.resolveStateRefs(em, allStateRefs)
                    .associateBy { StateRef(parseSecureHash(it.transactionId), it.leafIndex) }

                val inputStateAndRefs = transaction.inputStateRefs.map {
                    stateRefsToStateAndRefs[it]
                        ?: throw CordaRuntimeException("Could not find input StateRef $it when finding transaction $id")
                }
                val referenceStateAndRefs = transaction.referenceStateRefs.map {
                    stateRefsToStateAndRefs[it]
                        ?: throw CordaRuntimeException("Could not find reference StateRef $it when finding transaction $id")
                }

                SignedLedgerTransactionContainer(
                    transaction.wireTransaction,
                    inputStateAndRefs,
                    referenceStateAndRefs,
                    signatures
                )
            } else {
                null
            } to status
        }
    }

    override fun <T : ContractState> findUnconsumedVisibleStatesByType(stateClass: Class<out T>): List<UtxoVisibleTransactionOutputDto> {
        return entityManagerFactory.transaction { em ->
            repository.findUnconsumedVisibleStatesByType(em)
        }.filter {
            val contractState = serializationService.deserialize<ContractState>(it.data)
            stateClass.isInstance(contractState)
        }
    }

    override fun resolveStateRefs(stateRefs: List<StateRef>): List<UtxoVisibleTransactionOutputDto> {
        return entityManagerFactory.transaction { em ->
            repository.resolveStateRefs(em, stateRefs)
        }
    }

    override fun persistTransaction(
        transaction: UtxoTransactionReader,
        utxoTokenMap: Map<StateRef, UtxoToken>
    ): List<CordaPackageSummary> {
        entityManagerFactory.transaction { em ->
            return persistTransaction(em, transaction, utxoTokenMap)
        }
    }

    private fun persistTransaction(
        em: EntityManager,
        transaction: UtxoTransactionReader,
        utxoTokenMap: Map<StateRef, UtxoToken> = emptyMap()
    ): List<CordaPackageSummary> {
        val nowUtc = utcClock.instant()
        val transactionIdString = transaction.id.toString()

        val metadataBytes = transaction.rawGroupLists[0][0]
        val metadataHash = sandboxDigestService.hash(metadataBytes, DigestAlgorithmName.SHA2_256).toString()

        val metadata = transaction.metadata
        repository.persistTransactionMetadata(
            em,
            metadataHash,
            metadataBytes,
            requireNotNull(metadata.getMembershipGroupParametersHash()) { "Metadata without membership group parameters hash" },
            requireNotNull(metadata.getCpiMetadata()) { "Metadata without CPI metadata" }.fileChecksum
        )

        // Insert the Transaction
        repository.persistTransaction(
            em,
            transactionIdString,
            transaction.privacySalt.bytes,
            transaction.account,
            nowUtc,
            transaction.status,
            metadataHash
        )

        // Insert the Transactions components
        transaction.rawGroupLists.mapIndexed { groupIndex, leaves ->
            leaves.mapIndexed { leafIndex, data ->
                repository.persistTransactionComponentLeaf(
                    em,
                    transactionIdString,
                    groupIndex,
                    leafIndex,
                    data,
                    sandboxDigestService.hash(data, DigestAlgorithmName.SHA2_256).toString()
                )
            }
        }

        // Insert inputs data
        transaction.getConsumedStateRefs().forEachIndexed { index, input ->
            repository.persistTransactionSource(
                em,
                transactionIdString,
                UtxoComponentGroup.INPUTS.ordinal,
                index,
                input.transactionId.toString(),
                input.index
            )
        }

        // Insert reference data
        transaction.getReferenceStateRefs().forEachIndexed { index, reference ->
            repository.persistTransactionSource(
                em,
                transactionIdString,
                UtxoComponentGroup.REFERENCES.ordinal,
                index,
                reference.transactionId.toString(),
                reference.index
            )
        }

        // Insert outputs data
        transaction.getVisibleStates().entries.forEach { (stateIndex, stateAndRef) ->
            val utxoToken = utxoTokenMap[stateAndRef.ref]
            repository.persistVisibleTransactionOutput(
                em,
                transactionIdString,
                UtxoComponentGroup.OUTPUTS.ordinal,
                stateIndex,
                stateAndRef.state.contractState::class.java.canonicalName,
                nowUtc,
                consumed = false,
                CustomRepresentation(extractJsonDataFromState(stateAndRef)),
                utxoToken?.poolKey?.tokenType,
                utxoToken?.poolKey?.issuerHash?.toString(),
                stateAndRef.state.notaryName.toString(),
                utxoToken?.poolKey?.symbol,
                utxoToken?.filterFields?.tag,
                utxoToken?.filterFields?.ownerHash?.toString(),
                utxoToken?.amount
            )
        }

        // Mark inputs as consumed
        if (transaction.status == TransactionStatus.VERIFIED) {
            val inputStateRefs = transaction.getConsumedStateRefs()
            if (inputStateRefs.isNotEmpty()) {
                repository.markTransactionVisibleStatesConsumed(
                    em,
                    inputStateRefs,
                    nowUtc
                )
            }
        }

        // Insert the Transactions signatures
        transaction.signatures.forEachIndexed { index, digitalSignatureAndMetadata ->
            repository.persistTransactionSignature(
                em,
                transactionIdString,
                index,
                digitalSignatureAndMetadata,
                nowUtc
            )
        }
        return emptyList()
    }

    override fun persistTransactionIfDoesNotExist(transaction: UtxoTransactionReader): Pair<String?, List<CordaPackageSummary>> {
        entityManagerFactory.transaction { em ->
            val transactionIdString = transaction.id.toString()

            val status = repository.findTransactionStatus(em, transactionIdString)

            if (status != null) {
                return status to emptyList()
            }

            val cpkDetails = persistTransaction(em, transaction)

            return null to cpkDetails
        }
    }

    override fun updateStatus(id: String, transactionStatus: TransactionStatus) {
        entityManagerFactory.transaction { em ->
            repository.updateTransactionStatus(em, id, transactionStatus, utcClock.instant())
        }
    }

    private fun extractJsonDataFromState(stateAndRef: StateAndRef<*>): String {
        val contractState = stateAndRef.state.contractState
        val jsonMap = factoryStorage.getFactoriesForClass(contractState).associate {
            val jsonToParse = try {
                @Suppress("unchecked_cast")
                (it as ContractStateVaultJsonFactory<ContractState>)
                    .create(contractState, jsonMarshallingService)
                    .ifBlank { "{}" } // Default to "{}" if the provided factory returns empty string to avoid exception
            } catch (e: Exception) {
                // We can't log the JSON string here because the failed before we have a JSON
                log.warn("Error while processing factory for class: ${it.stateType.name}. Defaulting to empty JSON.", e)
                "{}"
            }

            it.stateType.name to try {
                jsonMarshallingService.parse(jsonToParse, Any::class.java)
            } catch (e: Exception) {
                log.warn(
                    "Error while processing factory for class: ${it.stateType.name}. " +
                        "JSON that could not be processed: $jsonToParse. Defaulting to empty JSON.",
                    e
                )
                jsonMarshallingService.parse("{}", Any::class.java)
            }
        }.toMutableMap()

        try {
            jsonMap[ContractState::class.java.name] = jsonMarshallingService.parse(
                defaultContractStateVaultJsonFactory.create(stateAndRef, jsonMarshallingService),
                Any::class.java
            )
        } catch (e: Exception) {
            log.warn(
                "Error while processing factory for class: ${ContractState::class.java.name}. Defaulting to empty JSON.",
                e
            )
            jsonMarshallingService.parse("{}", Any::class.java)
        }

        return try {
            jsonMarshallingService.format(jsonMap)
        } catch (e: JsonProcessingException) {
            // Since we validate the factory outputs one-by-one this should not happen.
            log.warn("Error while formatting combined JSON, defaulting to empty JSON.", e)
            "{}"
        }
    }

    override fun findSignedGroupParameters(hash: String): SignedGroupParameters? {
        return entityManagerFactory.transaction { em ->
            repository.findSignedGroupParameters(em, hash)
        }
    }

    override fun persistSignedGroupParametersIfDoNotExist(signedGroupParameters: SignedGroupParameters) {
        val hash = signedGroupParameters.groupParameters.array().hash(DigestAlgorithmName.SHA2_256).toString()
        if (findSignedGroupParameters(hash) == null) {
            entityManagerFactory.transaction { em ->
                repository.persistSignedGroupParameters(
                    em,
                    hash,
                    signedGroupParameters,
                    utcClock.instant()
                )
            }
        }
    }

    override fun persistMerkleProof(
        transactionId: String,
        groupIndex: Int,
        treeSize: Int,
        leaves: List<Int>,
        hashes: List<String>
    ) {
        return entityManagerFactory.transaction { em ->
            val persistedMerkleProofId = repository.persistMerkleProof(
                em,
                transactionId,
                groupIndex,
                treeSize,
                leaves,
                hashes
            )

            leaves.forEach { leafIndex ->
                repository.persistMerkleProofLeaf(em, persistedMerkleProofId, leafIndex)
            }
        }
    }

    private fun findFilteredTransactions(
        ids: List<String>
    ): Map<SecureHash, FilteredTransaction> {
        return entityManagerFactory.transaction { em ->
            repository.findFilteredTransactions(em, ids)
        }.map { (transactionId, ftxDto) ->
            // Map through each found transaction

            // 1. Parse the metadata bytes
            val filteredTransactionMetadata = parseMetadata(
                ftxDto.metadataBytes,
                jsonValidator,
                jsonMarshallingService
            )

            // 2. Merge the Merkle proofs for each component group
            val mergedMerkleProofs = ftxDto.merkleProofMap.mapValues { (_, merkleProofDtoList) ->

                merkleProofDtoList.map { merkleProofDto ->
                    // Transform the MerkleProofDto objects to MerkleProof objects
                    merkleProofFactory.createAuditMerkleProof(
                        merkleProofDto.transactionId,
                        merkleProofDto.groupIndex,
                        merkleProofDto.treeSize,
                        merkleProofDto.leavesWithData,
                        merkleProofDto.hashes
                    )
                }.reduce { accumulator, merkleProof ->
                    // Then  keep merging the elements into each other
                    (accumulator as MerkleProofInternal).merge(
                        merkleProof,
                        createHashDigestProvider(filteredTransactionMetadata, merkleTreeProvider)
                    )
                }
            }

            // 3. Create the top level Merkle proof by serializing the root of each merged Merkle proof
            // (i.e. component group Merkle proof)
            val topLevelMerkleProof = merkleProofFactory.createAuditMerkleProof(
                transactionId,
                0,
                mergedMerkleProofs.size,
                mergedMerkleProofs.map { (groupIndex, mergedMerkleProof) ->
                    groupIndex to serializationService.serialize(
                        mergedMerkleProof.calculateRoot(
                            createTopLevelDigestProvider(
                                filteredTransactionMetadata,
                                merkleTreeProvider
                            )
                        )
                    ).bytes
                }.toMap(),
                emptyList()
            )

            // 4. Create the filtered transaction
            val filteredTransaction = filteredTransactionFactory.create(
                parseSecureHash(transactionId),
                topLevelMerkleProof,
                mergedMerkleProofs.map {
                    it.key to FilteredComponentGroup(it.key, it.value)
                }.toMap(),
                ftxDto.privacySalt.bytes,
                ftxDto.metadataBytes
            )

            filteredTransaction.id to filteredTransaction
        }.toMap()
    }

    override fun findMerkleProofs(
        transactionId: String,
        groupIndex: Int
    ): List<MerkleProofDto> {
        return entityManagerFactory.transaction { em ->
            repository.findMerkleProofs(em, transactionId, groupIndex)
        }
    }
}