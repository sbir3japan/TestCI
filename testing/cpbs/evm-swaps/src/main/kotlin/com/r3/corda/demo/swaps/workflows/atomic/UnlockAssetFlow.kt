package com.r3.corda.demo.swaps.workflows.atomic

import com.r3.corda.demo.swaps.states.swap.LockState
import com.r3.corda.demo.swaps.states.swap.OwnableState
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

@Suppress("unused")
@InitiatingFlow(protocol = "unlock-asset-flow")
class UnlockAssetFlow : ClientStartableFlow {

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

            val outputStateAndRefs = signedTransaction.outputStateAndRefs

            @Suppress("UNCHECKED_CAST") val lockState =
                outputStateAndRefs.singleOrNull { it.state.contractState is LockState } as? StateAndRef<LockState>
                    ?: throw IllegalArgumentException("Transaction $transactionId does not have a lock state")

            @Suppress("UNCHECKED_CAST") val assetState =
                outputStateAndRefs.singleOrNull { it.state.contractState !is OwnableState } as? StateAndRef<OwnableState>
                    ?: throw IllegalArgumentException("Transaction $transactionId does not have a single asset")

            val signatures: List<DigitalSignature.WithKeyId> = DraftTxService(persistenceService, serializationService).blockSignatures(blockNumber)

//            require(signatures.count() >= lockState.state.contractState.signaturesThreshold) {
//                "Insufficient signatures for this transaction"
//            }

            // TODO: continue testing from here after the GetBlockByNumberSubFlow issue is resolved.
            // Get the block that mined the transaction that generated the designated EVM event
            val block = flowEngine.subFlow(GetBlockByNumberSubFlow(blockNumber, true))

            // Get all the transaction receipts from the block to build and verify the transaction receipts root
            val receipts = flowEngine.subFlow(GetBlockReceiptsSubFlow(blockNumber))

            // Get the receipt specifically associated with the transaction that generated the event
            val unlockReceipt = receipts[transactionIndex.toInt()]

            val merkleProof = generateMerkleProof(receipts, unlockReceipt)

            val unlockData = UnlockData(merkleProof, signatures, block.receiptsRoot, unlockReceipt)

            val transaction =
                flowEngine.subFlow(UnlockTransactionAndObtainAssetSubFlow(assetState, lockState, unlockData))

            return jsonMarshallingService.format(transaction)
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
