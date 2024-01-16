package com.r3.corda.demo.swaps.workflows.swap

import com.r3.corda.demo.swaps.TransactionBytes
import com.r3.corda.demo.swaps.contracts.swap.LockStateContract
import com.r3.corda.demo.swaps.states.swap.LockState
import com.r3.corda.demo.swaps.states.swap.OwnableState
import com.r3.corda.demo.swaps.IUnlockEventEncoder
import com.r3.corda.demo.swaps.SwapVaultEventEncoder
import net.corda.v5.application.crypto.CompositeKeyGenerator
import net.corda.v5.application.flows.*
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.CompositeKeyNodeAndWeight
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import org.slf4j.LoggerFactory
import java.security.PublicKey
import java.time.Duration
import java.time.Instant

/**
 * RequestLockFlow input parameters
 */
@CordaSerializable
data class RequestLockFlowArgs(
    val transactionId: SecureHash,
    val assetType: String,
    val lockToRecipient: String,
    val signaturesThreshold: Int,
    val validators: List<PublicKey>,
    val unlockEvent: IUnlockEventEncoder
)

/**
 * RequestLockFlow outputs parameters
 */
@CordaSerializable
data class RequestLockFlowResponse(
    val transactionId: String
)

/**
 * Initiating flow which builds a draft transaction that puts the Corda asset in a locked state.
 * The locking works by encumbering the asset represented by an [OwnableState] with a [LockState].
 * In this locked state, the asset is owned by a composite key built from the original owner and the
 * intended new owner.
 *
 * The draft transaction is sent to the counterparty together with its dependencies for verification.
 *
 * @param transactionId The transaction that holds the Ownable state at output index 0 (probably stateref would be best)
 * @param assetType the data type of the asset (e.g. com.r3.corda.demo.swaps.contracts.swap.AssetState)
 * @param lockToRecipient the recipient of the asset being locked
 * @param signaturesThreshold the minimum number of validators that should produce a valid signature to allow unlock
 * @param validators the identity of the Oracles whose signature will be requested to
 * @param unlockEvent the partially encoded event that will be used by the lock-state when validating unlock conditions
 */
@InitiatingFlow(protocol = "lock-asset-cs")
class RequestLockFlow : ClientStartableFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        val (transactionId, assetType, lockToRecipient, signaturesThreshold, validators, unlockEvent) = requestBody.getRequestBodyAs(
            jsonMarshallingService,
            RequestLockFlowArgs::class.java
        )

        val response = flowEngine.subFlow(
            RequestLockSubFlow(transactionId.toString(), assetType, lockToRecipient, signaturesThreshold, validators, unlockEvent)
        )

        return jsonMarshallingService.format(RequestLockFlowResponse(response))
    }
}

/**
 * Initiating flow which builds a draft transaction that puts the Corda asset in a locked state.
 * The locking works by encumbering the asset represented by an [OwnableState] with a [LockState].
 * In this locked state, the asset is owned by a composite key built from the original owner and the
 * intended new owner.
 *
 * The draft transaction is sent to the counterparty together with its dependencies for verification.
 *
 * @param transactionId The transaction that holds the Ownable state at output index 0 (probably stateref would be best)
 * @param assetType the data type of the asset (e.g. com.r3.corda.demo.swaps.contracts.swap.AssetState)
 * @param lockToRecipient the recipient of the asset being locked
 * @param signaturesThreshold the minimum number of validators that should produce a valid signature to allow unlock
 * @param validators the identity of the Oracles whose signature will be requested to
 * @param unlockEvent the partially encoded event that will be used by the lock-state when validating unlock conditions
 */
@InitiatingFlow(protocol = "lock-asset-sf")
class RequestLockSubFlow(
    val transactionId: String,
    val assetType: String, // the Corda asset type as com.r3.....MyAsset
    val lockToRecipient: String,
    val signaturesThreshold: Int,
    val validators: List<PublicKey>,
    val unlockEvent: IUnlockEventEncoder
) : SubFlow<String> {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var persistenceService: PersistenceService

    @CordaInject
    lateinit var serializationService: SerializationService

    @CordaInject
    lateinit var compositeKeyGenerator: CompositeKeyGenerator

    @Suspendable
    override fun call(): String {
        try {
            val senderKey = memberLookup.myInfo().ledgerKeys.first()
            val (recipientKey, recipientName) = with(
                memberLookup.lookup(MemberX500Name.parse(lockToRecipient))
                    ?: throw CordaRuntimeException("MemberLookup can't find recipient specified in flow arguments.")
            ) { Pair(ledgerKeys.first(), name) }

            val stateAndRef =
                ledgerService.findUnconsumedStatesByExactType(convertToClass(assetType), 100, Instant.now()).results
                    .singleOrNull { it.ref.transactionId.toString() == transactionId }
                    ?: throw CordaRuntimeException("No unique OwnableState found for transaction $transactionId")

            val sender = stateAndRef.state.contractState.owner
            val ownableState = with(stateAndRef.state.contractState) {
                //withNewOwner(compositeOwnership(owner, recipientKey))
                withNewOwner(recipientKey)
            }

            val lockState = LockState(
                assetSender = senderKey,
                assetRecipient = recipientKey,
                notary = stateAndRef.state.notaryKey,
                approvedValidators = validators,
                signaturesThreshold = signaturesThreshold,
                unlockEvent = unlockEvent
            )

            val txBuilder = ledgerService.createTransactionBuilder()
                .setNotary(stateAndRef.state.notaryName)
                .setTimeWindowUntil(Instant.now() + Duration.ofHours(1))
                .addInputState(stateAndRef.ref)
                .addEncumberedOutputStates("lock-" + lockState.linearId, lockState, ownableState)
                .addCommand(LockStateContract.LockCommands.Lock())
                .addSignatories(listOf(sender))

            val signedTransaction = txBuilder.toSignedTransaction()

            val recipientSession = flowMessaging.initiateFlow(recipientName)
            flowMessaging.sendAll(signedTransaction, setOf(recipientSession))

            // TODO: send all transaction dependencies for counterparty backchain validation

            // check the counterparty received and successfully validated transaction and backchain
            val sendingTransactionSuccess = recipientSession.receive(Boolean::class.java)
            if (!sendingTransactionSuccess) {
                throw CordaRuntimeException("Counterparty failed receiving transaction")
            }

            val serializedTx = serializationService.serialize(signedTransaction).bytes
            persistenceService.persist(TransactionBytes(signedTransaction.id.toString(), serializedTx))

            return signedTransaction.id.toString()
        } catch (e: Exception) {
            log.warn("Failed to build/propose draft transaction.", e)
            throw CordaRuntimeException("Failed to build/propose draft transaction.", e)
        }
    }

    private fun <T : OwnableState> convertToClass(typeAsString: String): Class<T> {
        return try {
            val clazz = Class.forName(typeAsString)
            if (ContractState::class.java.isAssignableFrom(clazz)) {
                @Suppress("UNCHECKED_CAST")
                clazz as Class<T>
            } else {
                throw CordaRuntimeException("Class $typeAsString does not represent a subclass of ContractState")
            }
        } catch (e: ClassNotFoundException) {
            throw CordaRuntimeException("Class $typeAsString could not be found", e)
        }
    }

    private fun compositeOwnership(currentOwner: PublicKey, newOwner: PublicKey): PublicKey {
        return compositeKeyGenerator.create(
            listOf(
                CompositeKeyNodeAndWeight(currentOwner, 1),
                CompositeKeyNodeAndWeight(newOwner, 1)
            ), 1
        )
    }
}

@Suppress("UNUSED")
@InitiatedBy(protocol = "lock-asset-cs")
class RequestLockFlowResponderCs : ResponderFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var persistenceService: PersistenceService

    @CordaInject
    lateinit var serializationService: SerializationService

    @Suspendable
    override fun call(session: FlowSession) {
        try {
            val signedTransaction = session.receive(UtxoSignedTransaction::class.java)

            // receive and validate all transaction's dependencies

            val serializedTx = serializationService.serialize(signedTransaction).bytes
            persistenceService.persist(TransactionBytes(signedTransaction.id.toString(), serializedTx))

            session.send(true)
        } catch (e: Exception) {
            log.warn("Failed to receive the draft transaction.", e)

            session.send(false)
        }
    }
}

@Suppress("UNUSED")
@InitiatedBy(protocol = "lock-asset-sf")
class RequestLockFlowResponderSf : ResponderFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var persistenceService: PersistenceService

    @CordaInject
    lateinit var serializationService: SerializationService

    @Suspendable
    override fun call(session: FlowSession) {
        try {
            val signedTransaction = session.receive(UtxoSignedTransaction::class.java)

            // receive and validate all transaction's dependencies

            val serializedTx = serializationService.serialize(signedTransaction).bytes
            persistenceService.persist(TransactionBytes(signedTransaction.id.toString(), serializedTx))

            session.send(true)
        } catch (e: Exception) {
            log.warn("Failed to receive the draft transaction.", e)

            session.send(false)
        }
    }
}

/**
 * RequestLockByEventFlow input parameters
 */
@CordaSerializable
data class RequestLockByEventFlowArgs(
    val transactionId: String,
    val assetType: String, // the Corda asset type as com.r3.....MyAsset
    val lockToRecipient: String,
    val signaturesThreshold: Int,
    val evmSigners: List<String>, // e.g.: [ "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266", "0x70997970C51812dc3A010C7d01b50e0d17dc79C8" ]
    val validators: List<String>, // e.g.: [ "CN=Testing, OU=Application, O=R3, L=London, C=GB", "CN=EVM, OU=Application, O=Ethereum, L=Brussels, C=BE" ]
    val chainId: Int, // BigInt?
    val protocolAddress: String,
    val evmSender: String,
    val evmRecipient: String,
    val tokenAddress: String,
    val amount: Int, // BigInt?
    val tokenId: Int, // BigInt?
)

/**
 * Initiating flow which builds a draft transaction that puts the Corda asset in a locked state.
 * The locking works by encumbering the asset represented by an [OwnableState] with a [LockState].
 * In this locked state, the asset is owned by a composite key built from the original owner and the
 * intended new owner.
 *
 * Compared to the RequestLockFlow, this flow builds the encoded event internally form the flow's input parameters.
 *
 * The draft transaction is sent to the counterparty together with its dependencies for verification.
 *
 * @param transactionId The transaction that holds the Ownable state at output index 0 (probably stateref would be best)
 * @param assetType the data type of the asset (e.g. com.r3.corda.demo.swaps.contracts.swap.AssetState)
 * @param lockToRecipient the recipient of the asset being locked
 * @param signaturesThreshold the minimum number of validators that should produce a valid signature to allow unlock
 *
 * @param evmSigners the EVM identity of the Oracles whose signature will be requested to (proof of notarization)
 *                   e.g.: [ "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266", "0x70997970C51812dc3A010C7d01b50e0d17dc79C8" ]
 *
 * @param validators the Corda X500 identity of the Oracles whose signature will be requested to (proof of claim/revert)
 *                   e.g.: [ "CN=Testing, OU=Application, O=R3, L=London, C=GB",
 *                           "CN=EVM, OU=Application, O=Ethereum, L=Brussels, C=BE" ]
 *
 * @param chainId the chainId that will be hash encoded into the expected event
 * @param protocolAddress the EVM Swap Contract deployment address
 * @param evmSender the EVM account that commits the asset to the Swap Protocol
 * @param evmRecipient the EVM account of the recipient of the asset that is committed to the Swap Protocol
 * @param tokenAddress the EVM contract address of the asset committed to the Swap Protocol
 * @param amount the amount of asset committed to the Swap Protocol
 * @param tokenId the token ID for ERC712 / ERC1155 tokens, 0 for ERC20 tokens.
 */
@InitiatingFlow(protocol = "lock-asset-by-event")
class RequestLockByEventFlow : ClientStartableFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        val args = requestBody.getRequestBodyAs(
            jsonMarshallingService,
            RequestLockByEventFlowArgs::class.java
        )

        val swapVaultEventEncoder = SwapVaultEventEncoder.create(
            chainId = args.chainId.toBigInteger(),
            protocolAddress = args.protocolAddress,
            owner = args.evmSender,
            recipient = args.evmRecipient,
            amount = args.amount.toBigInteger(),
            tokenId = args.tokenId.toBigInteger(),
            tokenAddress = args.tokenAddress,
            signaturesThreshold = args.signaturesThreshold.toBigInteger(),
            signers = args.evmSigners // same as validators but their EVM identity instead
        )

        val validators = args.validators.map {
            memberLookup.lookup(MemberX500Name.parse(it))?.ledgerKeys?.first() ?: throw CordaRuntimeException("Member $it not found")
        }

        val flowParas = RequestLockSubFlow(
            args.transactionId,
            args.assetType,
            args.lockToRecipient,
            args.signaturesThreshold,
            validators,
            swapVaultEventEncoder
        )

        return flowEngine.subFlow(flowParas)
    }
}
