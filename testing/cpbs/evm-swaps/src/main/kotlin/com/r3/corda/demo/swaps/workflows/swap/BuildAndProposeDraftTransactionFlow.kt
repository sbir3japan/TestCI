//package com.r3.corda.demo.swaps.workflows.swap
//
//
//import com.r3.corda.demo.swaps.contracts.swap.LockCommand
//import com.r3.corda.demo.swaps.states.swap.LockState
//import com.r3.corda.demo.swaps.states.swap.OwnableState
//import net.corda.v5.application.flows.*
//import net.corda.v5.application.marshalling.JsonMarshallingService
//import net.corda.v5.application.membership.MemberLookup
//import net.corda.v5.application.messaging.FlowMessaging
//import net.corda.v5.application.messaging.FlowSession
//import net.corda.v5.base.annotations.Suspendable
//import net.corda.v5.base.types.MemberX500Name
//import net.corda.v5.ledger.common.NotaryLookup
//import net.corda.v5.ledger.utxo.UtxoLedgerService
//import org.slf4j.LoggerFactory
//import java.time.Instant
//import java.util.*
//
//class IssueCurrencyInputs {
//    val symbol: String? = null
//    val amount: Int? = null
//}
//
//data class SwapTransactionDetails(
//    val senderCordaName: MemberX500Name,
//    val receiverCordaName: MemberX500Name,
//    val cordaAssetState: UUID,
//    val approvedCordaValidators: List<MemberX500Name>,
//    val minimumNumberOfEventValidations: Int,
//)
//
//data class BuildAndProposeDraftTransactionFlowInput(
//    val swapTransactionDetails: SwapTransactionDetails,
//    val commitmentHash: ByteArray
//)
//
//@Suppress("unused")
//@InitiatingFlow(protocol = "build-and-propose-draft-transaction-flow")
//class BuildAndProposeDraftTransactionFlow : ClientStartableFlow {
//
//    private companion object {
//        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
//    }
//
//    @CordaInject
//    lateinit var jsonMarshallingService: JsonMarshallingService
//
//    @CordaInject
//    lateinit var memberLookup: MemberLookup
//
//    @CordaInject
//    lateinit var ledgerService: UtxoLedgerService
//
//    @CordaInject
//    lateinit var notaryLookup: NotaryLookup
//
//    @CordaInject
//    lateinit var flowMessaging: FlowMessaging
//
//    @CordaInject
//    lateinit var flowEngine: FlowEngine
//
//    /**
//     * This function builds issues a currency on Corda
//     */
//    @Suspendable
//    override fun call(requestBody: ClientRequestBody): String {
//        try {
//            // Switch the ownable state
//            val inputs = requestBody.getRequestBodyAs(
//                jsonMarshallingService,
//                BuildAndProposeDraftTransactionFlowInput::class.java
//            )
//
//            // Fetch the ownable state from the UUID
//            val ownableState = ledgerService.findUnconsumedStatesByExactType(
//                OwnableState::class.java,
//                100,
//                Instant.now()
//            ).results.filter {
//                it.state.contractState.linearId == inputs.swapTransactionDetails.cordaAssetState
//            }.first()
//
//            // set the new owner
//            val newOwnableState =
//                ownableState.state.contractState.withNewOwner(inputs.swapTransactionDetails.receiverCordaName)
//            val key = memberLookup.myInfo().ledgerKeys.first()
//
//            val otherUser = memberLookup.lookup().filter {
//                it.name == inputs.swapTransactionDetails.receiverCordaName
//            }.first().ledgerKeys.first()
//            val notary= notaryLookup.notaryServices.single()
//
//            val lockState = LockState(
//                key,
//                otherUser,
//                notary.publicKey,
//                inputs.swapTransactionDetails.approvedCordaValidators.map { memberLookup.lookup(it).ledgerKeys.first() },
//                inputs.swapTransactionDetails.minimumNumberOfEventValidations,
//                linearId = UUID.randomUUID(),
//                participants = listOf(key, otherUser)
//            )
//
//            val participants = memberLookup.lookup().filter {
//                it.memberProvidedContext["corda.notary.service.name"] != notaryName.toString()
//            }.map {
//                it.ledgerKeys.first()
//            }
//
//            val txBuilder = ledgerService.createTransactionBuilder()
//                .setNotary(notary.name)
//                .addInputState(ownableState.ref)
//                .addOutputState(newOwnableState)
//                .addOutputState(lockState)
//                .addSignatories(key)
//                .addCommand(LockCommand.Lock)
//                .setTimeWindowUntil(Instant.now().plusSeconds(300000))
//            ledgerService.
//
//            val signedTransaction = txBuilder.toSignedTransaction()
//
//            signedTransaction.
//
//
//            // Get any of the relevant details from the request here
//
//            // Save the users key
//
//            // Get the notary name
//
//            // Get the key of the other participant
///
//
//            flowMessaging.initiateFlow(inputs.swapTransactionDetails.receiverCordaName)
//
//
//
//            // Finalize the transaction
////            val output = ledgerService.finalize(
////                txBuilder.toSignedTransaction(),
////                sessions
////            )
//
//            // Returns the transaction id
////            return output.transaction.id.toString()
//
//        } catch (e: Exception) {
//            log.error("Unexpected error while processing Issue Currency Flow ", e)
//            throw e
//        }
//
//
//    }
//
////    @Suspendable
////    private fun sendTransactionDetails(session: FlowSession, wireTx: WireTransaction) {
////        session.send(wireTx)
////        val wireTxDependencies = wireTx.inputs.map { it.txhash }.toSet() + wireTx.references.map { it.txhash }.toSet()
////        wireTxDependencies.forEach {
////            serviceHub.validatedTransactions.getTransaction(it)?.let { stx ->
////                subFlow(SendTransactionFlow(session, stx))
////            }
////        }
////    }
////
////    @Suspendable
////    private fun handleVerificationResult(draftTxVerificationResult: Boolean, wireTx: WireTransaction): WireTransaction? {
////        return if (draftTxVerificationResult) {
////            serviceHub.cordaService(DraftTxService::class.java).saveDraftTx(wireTx)
////            wireTx
////        } else {
////            null
////        }
////    }
////
////    @Suspendable
////    private fun constructLockedAsset(asset: OwnableState, newOwner: Party): OwnableState {
////        // Build composite key
////        val compositeKey =  CompositeKey.Builder()
////            .addKey(asset.owner.owningKey, weight = 1)
////            .addKey(newOwner.owningKey, weight = 1)
////            .build(1)
////
////
////        return asset.withNewOwner(AnonymousParty(compositeKey)).ownableState
////    }
////
//
//}
//
//
//@Suppress("unused")
//@InitiatedBy(protocol = "issue-currency-flow")
//class FinalizeIssueCurrencySubFlow : ResponderFlow {
//
//    @CordaInject
//    lateinit var utxoLedgerService: UtxoLedgerService
//
//    @Suspendable
//    override fun call(session: FlowSession) {
//        // Receive, verify, validate, sign and record the transaction sent from the initiator
//        utxoLedgerService.receiveFinality(session) {
//
//        }
//    }
//}