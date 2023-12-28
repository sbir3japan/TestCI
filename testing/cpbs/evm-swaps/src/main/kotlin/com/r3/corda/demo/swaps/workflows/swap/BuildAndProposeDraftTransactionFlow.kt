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
import org.abi.datatypes.Address
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.security.PublicKey
import java.time.Duration
import java.time.Instant

/**
 *
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
 *
 */
@CordaSerializable
data class RequestLockFlowResponse(
    val transactionId: String
)

/**
 * (Client Startable) RequestLockFlow
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
            RequestLockSubFlow(transactionId, assetType, lockToRecipient, signaturesThreshold, validators, unlockEvent)
        )

        return jsonMarshallingService.format(RequestLockFlowResponse(response))
    }
}

/**
 * (SubFlow) RequestLockFlow
 */
@InitiatingFlow(protocol = "lock-asset-sf")
class RequestLockSubFlow(
    val transactionId: SecureHash,
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
                    .singleOrNull { it.ref.transactionId == transactionId }
                    ?: throw CordaRuntimeException("No unique OwnableState found for transaction $transactionId")

            val sender = stateAndRef.state.contractState.owner
            val ownableState = with(stateAndRef.state.contractState) {
                withNewOwner(compositeOwnership(owner, recipientKey))
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
            throw CordaRuntimeException("Failed to build/propose draft transaction.", e)
        }
    }

    /**
     *
     */
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
 *
 */
@CordaSerializable
data class RequestLockByEventFlowArgs(
    val transactionId: SecureHash,
    val assetType: String, // the Corda asset type as com.r3.....MyAsset
    val lockToRecipient: String,
    val signaturesThreshold: Int,
    //val validators: List<PublicKey>,
    val chainId: Int, // BigInt?
    val protocolAddress: String,
    val evmSender: String,
    val evmRecipient: String,
    //val evmSigners: List<String>, // EMV Identities for Oracles to sign-as-evm-id and prove notarization
    val tokenAddress: String,
    val amount: Int, // BigInt?
    val tokenId: Int, // BigInt?
)

/**
 *
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
            //signers = args.evmSigners // same as validators but the EVM identity instead
            signers =  listOf("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266", "0x70997970C51812dc3A010C7d01b50e0d17dc79C8")
        )

        val validators = listOf(
            memberLookup.lookup(MemberX500Name.parse("CN=Testing, OU=Application, O=R3, L=London, C=GB"))?.ledgerKeys?.first() ?: throw CordaRuntimeException("Member not found"),
            memberLookup.lookup(MemberX500Name.parse("CN=EVM, OU=Application, O=Ethereum, L=Brussels, C=BE"))?.ledgerKeys?.first() ?: throw CordaRuntimeException("Member not found")
        )

        val flowParas = RequestLockSubFlow(
            args.transactionId,
            args.assetType,
            args.lockToRecipient,
            args.signaturesThreshold,
            validators,//args.validators,
            swapVaultEventEncoder
        )

        MemberX500Name.parse("CN=Testing, OU=Application, O=R3, L=London, C=GB")
        return flowEngine.subFlow(flowParas)
    }
}
