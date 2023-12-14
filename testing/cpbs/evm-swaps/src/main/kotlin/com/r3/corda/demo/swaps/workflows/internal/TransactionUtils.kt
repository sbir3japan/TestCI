package com.r3.corda.demo.swaps.workflows.internal


import net.corda.v5.application.flows.*
import net.corda.v5.application.interop.evm.Log
import net.corda.v5.application.interop.evm.TransactionReceipt


/**
 * Helper function to replicate a TransactionReceipt to a Corda serializable version of it.
 */
fun org.web3j.protocol.core.methods.response.TransactionReceipt.toSerializable(): TransactionReceipt {
    return TransactionReceipt(
       blockHash,
        blockNumber,
        contractAddress,
        cumulativeGasUsed,
        effectiveGasPrice.toBigInteger(),
        from,
        gasUsed,
        logs.map {
            Log(
                it.address,
                it.topics,
                it.data,
                it.blockNumber,
                it.transactionHash,
                it.transactionIndex,
                it.blockHash,
                it.logIndex.toInt(),
                it.isRemoved
            )
        },
        logsBloom,
        status.toBoolean(),
        to,
        transactionHash,
        transactionIndex,
        type
    )
}


//
///**
// * Collect signatures for the provided [SignedTransaction], from the list of [Party] provided.
// * This is an initiating flow, and is used where some required signatures are from [CompositeKey]s.
// * The standard Corda CollectSignaturesFlow will not work in this case.
// * @param stx - the [SignedTransaction] to sign
// * @param signers - the list of signing [Party]s
// */
//@InitiatingFlow
//internal class CollectSignaturesForComposites(
//    private val stx: SignedTransaction,
//    private val signers: List<Party>
//) : FlowLogic<SignedTransaction>() {
//
//    @Suspendable
//    override fun call(): SignedTransaction {
//
//        // create new sessions to signers and trigger the signing responder flow
//        val sessions = signers.map { initiateFlow(it) }
//
//        // We filter out any responses that are not TransactionSignature`s (i.e. refusals to sign).
//        val signatures = sessions
//            .map { it.sendAndReceive<Any>(stx).unwrap { data -> data } }
//            .filterIsInstance<TransactionSignature>()
//        return stx.withAdditionalSignatures(signatures)
//    }
//}
//
/**
 * Responder flow for [CollectSignaturesForComposites] flow.
 */
//@InitiatedBy(CollectSignaturesForComposites::class)
//internal class CollectSignaturesForCompositesHandler(private val otherPartySession: FlowSession) : FlowLogic<Unit>() {
//
//    @Suspendable
//    override fun call() {
//
//        otherPartySession.receive<SignedTransaction>().unwrap { partStx ->
//            // REVIEW: add conditions where we might not sign?
//
//            val returnStatus = serviceHub.createSignature(partStx)
//            otherPartySession.send(returnStatus)
//        }
//    }
//}
