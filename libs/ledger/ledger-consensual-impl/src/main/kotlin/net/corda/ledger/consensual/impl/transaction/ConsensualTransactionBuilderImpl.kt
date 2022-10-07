package net.corda.ledger.consensual.impl.transaction

import net.corda.ledger.common.impl.transaction.CpiSummary
import net.corda.ledger.common.impl.transaction.CpkSummary
import net.corda.ledger.common.impl.transaction.PrivacySaltImpl
import net.corda.ledger.common.impl.transaction.TransactionMetaData
import net.corda.ledger.common.impl.transaction.TransactionMetaData.Companion.CPI_METADATA_KEY
import net.corda.ledger.common.impl.transaction.TransactionMetaData.Companion.DIGEST_SETTINGS_KEY
import net.corda.ledger.common.impl.transaction.TransactionMetaData.Companion.LEDGER_MODEL_KEY
import net.corda.ledger.common.impl.transaction.TransactionMetaData.Companion.LEDGER_VERSION_KEY
import net.corda.ledger.common.impl.transaction.TransactionMetaData.Companion.PLATFORM_VERSION_KEY
import net.corda.ledger.common.impl.transaction.WireTransaction
import net.corda.ledger.common.impl.transaction.WireTransactionDigestSettings
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder
import java.security.PublicKey
import java.security.SecureRandom
import java.time.Instant

@Suppress("LongParameterList")
class ConsensualTransactionBuilderImpl(
    private val merkleTreeProvider: MerkleTreeProvider,
    private val digestService: DigestService,
    private val secureRandom: SecureRandom,
    private val serializer: SerializationService,
    private val signingService: SigningService,
    private val jsonMarshallingService: JsonMarshallingService,
    private val memberLookup: MemberLookup,
    private val sandboxCpks: Collection<CpkMetadata>,
    override val states: List<ConsensualState> = emptyList(),
) : ConsensualTransactionBuilder {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ConsensualTransactionBuilderImpl) return false
        if (other.states.size != states.size) return false

        return other.states.withIndex().all{
            it.value == states[it.index]
        }
    }

    override fun hashCode(): Int = states.hashCode()

    private fun copy(
        states: List<ConsensualState> = this.states
    ): ConsensualTransactionBuilderImpl {
        return ConsensualTransactionBuilderImpl(
            merkleTreeProvider, digestService, secureRandom, serializer, signingService, jsonMarshallingService,
            memberLookup, sandboxCpks,
            states,
        )
    }

    override fun withStates(vararg states: ConsensualState): ConsensualTransactionBuilder =
        this.copy(states = this.states + states)

    private fun calculateMetaData(): TransactionMetaData {
        return TransactionMetaData(
            mapOf(
                LEDGER_MODEL_KEY to ConsensualLedgerTransactionImpl::class.java.canonicalName,
                LEDGER_VERSION_KEY to TRANSACTION_META_DATA_CONSENSUAL_LEDGER_VERSION,
                DIGEST_SETTINGS_KEY to WireTransactionDigestSettings.defaultValues,
                PLATFORM_VERSION_KEY to memberLookup.myInfo().platformVersion,
                CPI_METADATA_KEY to getCpiMetadata()
            )
        )
    }

    /**
     * TODO(Fake values until we can get CPI information properly)
     */
    private fun getCpiIdentifier(): CpiIdentifier {
        return CpiIdentifier(
            "CPI name",
            "CPI version",
            SecureHash("SHA-256", "Fake-value".toByteArray()))
    }

    private fun getCpiMetadata(): CpiSummary {
        val cpiIdentifier = getCpiIdentifier()

        return CpiSummary(
            name = cpiIdentifier.name,
            version = cpiIdentifier.version,
            signerSummaryHash = cpiIdentifier.signerSummaryHash?.toHexString(),
            fileChecksum = "00000000000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",  // TODO: where do we get this?
            cpks = sandboxCpks.filter { it.isContractCpk() }.map { cpk ->
                CpkSummary(
                    name = cpk.cpkId.name,
                    version = cpk.cpkId.version,
                    signerSummaryHash = cpk.cpkId.signerSummaryHash?.toHexString() ?: "",
                    fileChecksum = cpk.fileChecksum.toHexString()
                )
            }
        )
    }

    private fun calculateComponentGroupLists(serializer: SerializationService): List<List<ByteArray>>
    {
        val requiredSigningKeys = states
            .map{it.participants}
            .flatten()
            .map{it.owningKey}
            .distinct()

        val componentGroupLists = mutableListOf<List<ByteArray>>()
        for (componentGroupIndex in ConsensualComponentGroupEnum.values()) {
            componentGroupLists += when (componentGroupIndex) {
                ConsensualComponentGroupEnum.METADATA ->
                    listOf(jsonMarshallingService.format(calculateMetaData()).toByteArray(Charsets.UTF_8)) // TODO(update with CORE-5940)
                ConsensualComponentGroupEnum.TIMESTAMP ->
                    listOf(serializer.serialize(Instant.now()).bytes)
                ConsensualComponentGroupEnum.REQUIRED_SIGNING_KEYS ->
                    requiredSigningKeys.map{serializer.serialize(it).bytes}
                ConsensualComponentGroupEnum.OUTPUT_STATES ->
                    states.map{serializer.serialize(it).bytes}
                ConsensualComponentGroupEnum.OUTPUT_STATE_TYPES ->
                    states.map{serializer.serialize(it::class.java.name).bytes}
            }
        }
        return componentGroupLists
    }

    override fun signInitial(publicKey: PublicKey): ConsensualSignedTransaction {
        val wireTransaction = buildWireTransaction()
        // TODO(CORE-5091 we just fake the signature for now...)
//        val signature = signingService.sign(wireTransaction.id.bytes, publicKey, SignatureSpec.RSA_SHA256)
        val signature = DigitalSignature.WithKey(publicKey, "0".toByteArray(), mapOf())
        val digitalSignatureMetadata = DigitalSignatureMetadata(Instant.now(), mapOf()) //CORE-5091 populate this properly...
        val signatureWithMetaData = DigitalSignatureAndMetadata(signature, digitalSignatureMetadata)
        return ConsensualSignedTransactionImpl(serializer, wireTransaction, listOf(signatureWithMetaData))
    }

    private fun buildWireTransaction() : WireTransaction{
        // TODO(CORE-5982 more verifications)
        // TODO(CORE-5940 ? metadata verifications: nulls, order of CPKs, at least one CPK?))
        require(states.isNotEmpty()){"At least one Consensual State is required"}
        require(states.all{it.participants.isNotEmpty()}){"All consensual states needs to have participants"}
        val componentGroupLists = calculateComponentGroupLists(serializer)

        val entropy = ByteArray(32)
        secureRandom.nextBytes(entropy)
        val privacySalt = PrivacySaltImpl(entropy)

        return WireTransaction(
            merkleTreeProvider,
            digestService,
            jsonMarshallingService,
            privacySalt,
            componentGroupLists
        )
    }
}
