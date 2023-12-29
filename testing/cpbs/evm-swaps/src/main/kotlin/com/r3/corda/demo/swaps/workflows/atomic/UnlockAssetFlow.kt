package com.r3.corda.demo.swaps.workflows.atomic

import com.r3.corda.demo.swaps.contracts.swap.LockCommand
import com.r3.corda.demo.swaps.states.swap.LockState
import com.r3.corda.demo.swaps.states.swap.OwnableState
import com.r3.corda.demo.swaps.states.swap.SerializableTransactionReceipt
import com.r3.corda.demo.swaps.states.swap.UnlockData
import com.r3.corda.demo.swaps.utils.trie.PatriciaTrie
import com.r3.corda.demo.swaps.utils.trie.SimpleKeyValueStore
import com.r3.corda.demo.swaps.workflows.eth2eth.GetBlockByNumberSubFlow
import com.r3.corda.demo.swaps.workflows.eth2eth.GetBlockReceiptsSubFlow
import com.r3.corda.demo.swaps.workflows.internal.DraftTxService
//import com.r3.corda.demo.swaps.workflows.internal.DraftTxService
import com.r3.corda.demo.swaps.workflows.swap.UnlockTransactionAndObtainAssetSubFlow
import java.math.BigInteger
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.interop.evm.Log
import net.corda.v5.application.interop.evm.TransactionReceipt
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.rlp.RlpEncoder
import org.rlp.RlpList
import org.rlp.RlpString
import org.slf4j.LoggerFactory
import org.utils.Numeric
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Suppress("unused")
@InitiatingFlow(protocol = "unlock-asset-flow")
class UnlockAssetFlow : ClientStartableFlow {

    private val defaultTimeWindowUpperBound: Instant =
        LocalDate.of(2200, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC)

    data class RequestParams(
        val transactionId: SecureHash,
        val blockNumber: BigInteger,
        val transactionIndex: BigInteger
    )

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

    /**
     * This function builds issues a currency on Corda
     */
    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        try {
            // Get any of the relevant details from the request here
            val (
                transactionId,
                blockNumber,
                transactionIndex
            ) = requestBody.getRequestBodyAs(jsonMarshallingService, RequestParams::class.java)

            val signedTransaction = ledgerService.findSignedTransaction(transactionId)
                ?: throw IllegalArgumentException("Transaction not found for ID: $transactionId")

            val outputStateAndRefs: MutableList<StateAndRef<*>> = signedTransaction.outputStateAndRefs

            @Suppress("UNCHECKED_CAST") val lockState =
                outputStateAndRefs.singleOrNull { it.state.contractState is LockState } as? StateAndRef<LockState>
                    ?: throw IllegalArgumentException("Transaction $transactionId does not have a lock state")

            @Suppress("UNCHECKED_CAST") val assetState =
                outputStateAndRefs.singleOrNull { it.state.contractState is OwnableState } as? StateAndRef<OwnableState>
                    ?: throw IllegalArgumentException("Transaction $transactionId does not have a single asset")

            val signatures: List<DigitalSignature.WithKeyId> = DraftTxService(persistenceService, serializationService).blockSignatures(blockNumber)

//            require(signatures.count() >= lockState.state.contractState.signaturesThreshold) {
//                "Insufficient signatures for this transaction"
//            }

            // TODO: continue testing from here after the GetBlockByNumberSubFlow issue is resolved.
            // Get the block that mined the transaction that generated the designated EVM event
            val block = flowEngine.subFlow(GetBlockByNumberSubFlow(blockNumber, false))

            // Get all the transaction receipts from the block to build and verify the transaction receipts root
            val receipts = flowEngine.subFlow(GetBlockReceiptsSubFlow(blockNumber))

            // Get the receipt specifically associated with the transaction that generated the event
            val unlockReceipt = receipts[transactionIndex.toInt()]

            val merkleProof = generateMerkleProof(receipts, unlockReceipt)

            val unlockData = UnlockData(merkleProof, signatures, block.receiptsRoot, SerializableTransactionReceipt.fromTransactionReceipt(unlockReceipt))

//            val transaction =
//                flowEngine.subFlow(UnlockTransactionAndObtainAssetSubFlow(assetState, lockState, unlockData))

            /*******************************************************************************************/
            val myInfo = memberLookup.myInfo()

//            val newOwner = memberLookup.lookup(lockState.state.contractState.assetRecipient)
//                ?: throw IllegalArgumentException("The specified recipient does not resolve to a known Party")

            // TODO: Unlock is using UnlockData which is not serializing (exception). Definitely the EVM Types must be marked as @CordaSerializable, but there seems to be something also on the Merkle
            val unlockCommand = LockCommand.Unlock(unlockData)

            val builder = ledgerService.createTransactionBuilder()
                .setNotary(lockState.state.notaryName)
                //.setTimeWindowBetween(Instant.now(), Instant.MAX) // TODO: how do I give infinite timeout without overflow error? There is a bug in C5 when using Instant.MAX
                .setTimeWindowUntil(Instant.now() + Duration.ofHours(1))
                .addInputStates(assetState.ref, lockState.ref)
                .addOutputState(assetState.state.contractState)
                .addCommand(unlockCommand)
                .addSignatories(myInfo.ledgerKeys.first())

            val stx = builder.toSignedTransaction()

            // TODO: @fowlerrr need to discuss/understand the implication of emptyList here, it may be correct bu still need to
            //       check what it means in C5
            val result = ledgerService.finalize(stx, emptyList())

            //return result.transaction
            val transaction = result.transaction
            /*******************************************************************************************/

            return jsonMarshallingService.format(transaction)
            // REVIEW: line above is throing exception. Tried adding jackson-datatype-jsr310 reference but still throwing.
            // Should probably neeed additional step to register JavaTimeModule but I believe it is or should be already
            // done by some core component
        } catch (e: Exception) {
            log.error("Unexpected error while processing Issue Currency Flow ", e)
            throw e
        }
    }

    @Suspendable
    public fun generateMerkleProof(
        receipts: List<TransactionReceipt>,
        unlockReceipt: TransactionReceipt
    ): SimpleKeyValueStore {
        // Build the trie
        val trie = PatriciaTrie()
        for (receipt in receipts) {
            trie.put(
                encodeKey(receipt.transactionIndex!!),
                receipt.encoded()
            )
        }

        return trie.generateMerkleProof(encodeKey(unlockReceipt.transactionIndex))
    }


    @Suspendable
    fun encodeKey(key: BigInteger): ByteArray = RlpEncoder.encode(RlpString.create(key))

}

fun TransactionReceipt.encoded(): ByteArray {
    fun serializeLog(log: Log): RlpList {
        val address = Numeric.hexStringToByteArray(log.address)
        val topics = log.topics.map { topic -> Numeric.hexStringToByteArray(topic) }

        require(address.size == 20) { "Invalid contract address size (${address.size})" }
        require(topics.isNotEmpty() && topics.all { it.size == 32 }) { "Invalid topics length or size" }

        return RlpList(
            listOf(
                RlpString.create(address),
                RlpList(topics.map { topic -> RlpString.create(topic) }),
                RlpString.create(Numeric.hexStringToByteArray(log.data))
            )
        )
    }

    return RlpEncoder.encode(
        RlpList(
            listOf(
                RlpString.create(if (this.status) "1" else "0"),
                RlpString.create(this.cumulativeGasUsed),
                RlpString.create(Numeric.hexStringToByteArray(this.logsBloom)),
                RlpList(this.logs.map { serializeLog(it) })
            )
        )
    )
}
