package com.r3.corda.demo.interop.evm

import com.corda.evm.contracts.TokenCommand
import com.corda.evm.states.FungibleTokenState
import net.corda.v5.application.flows.*
import java.math.BigInteger
import net.corda.v5.application.interop.evm.EvmService
import net.corda.v5.application.interop.evm.Parameter
import net.corda.v5.application.interop.evm.Type
import net.corda.v5.application.interop.evm.options.TransactionOptions
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.NotaryLookup
import org.slf4j.LoggerFactory
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import java.security.PublicKey
import java.time.Instant
import java.util.*


/**
 * This flow is used to demo the transfer of a corda fungible token in exchange for a fractionalised asset.
 */
@Suppress("unused")
@InitiatingFlow(protocol = "make-payment-flow")
class EvmDemoFlow : ClientStartableFlow {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val TRANSFER_FUNCTION = "sendTokenOne"
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    /**
     * This function builds the transaction that will be process the payment on Corda
     */
    @Suspendable
    private fun buildPaymentTransaction(inputs: EvmDemoInput, member: PublicKey, otherMember: PublicKey, notaryName: MemberX500Name): UtxoSignedTransaction {
        // Query the vault for the unconsumed state
        val states = ledgerService.findUnconsumedStatesByExactType(
            FungibleTokenState::class.java,
            100,
            Instant.now()
        )

        // Filter the states to get the one that matches the input id (and will be consumed)
        val filteredState = states.results.single { it.state.contractState.linearId == inputs.id }

        // Fetch the balance details
        val initialBalances = filteredState.state.contractState.balances

        // Update the balances to reflect the payment
        val updatedBalances = initialBalances.toMutableMap()
        updatedBalances[member] = updatedBalances[member]!! - inputs.purchasePrice!!.toLong()
        updatedBalances[otherMember] = updatedBalances[otherMember]!! + inputs.purchasePrice!!.toLong()

        // Create a new state
        val state = filteredState.state.contractState.copy(balances = updatedBalances, linearId = UUID.randomUUID())

        // Build the transaction
        val txBuilder = ledgerService.createTransactionBuilder()
            .setNotary(notaryName)
            .addInputState(filteredState.ref)
            .addOutputState(state)
            .addSignatories(member)
            .addCommand(TokenCommand.Spend)
            .setTimeWindowUntil(Instant.now().plusSeconds(300000))

        return txBuilder.toSignedTransaction()
    }


    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("Starting Evm Demo Flow...")
        try {
            // Get any of the relevant details from the request here
            val inputs = requestBody.getRequestBodyAs(jsonMarshallingService, EvmDemoInput::class.java)

            // Get the details of the current member
            val myInfo = memberLookup.myInfo()

            // Get the name of the notary
            val notaryName = notaryLookup.notaryServices.single().name

            // Because this is by default a two member network (excluding the notary) we simply look up the other member
            val otherMember = memberLookup.lookup().filter {
                it.memberProvidedContext["corda.notary.service.name"] != notaryName.toString() && it.name != myInfo.name
            }.map {
                it
            }.single()

            // Build the signed transaction
            val signedTransaction = buildPaymentTransaction(inputs, myInfo.ledgerKeys.first(), otherMember.ledgerKeys.first(), notaryName)

            // We initiate the subflow to send the transaction to the other member
            val session = flowMessaging.initiateFlow(otherMember.name)

            // Send a message containing the input details so that the counterparty can transfer the asset
            session.send(inputs)
            val hash = session.receive(String::class.java)

            // on receiving of a hash we finalize the transaction
            ledgerService.finalize(signedTransaction, listOf(session))

            // Return the hash to the client
            return jsonMarshallingService.format(EvmDemoOutput(hash))


        } catch (e: Exception) {
            log.error("Unexpected error while processing the EVM Demo Flow", e)
            throw e
        }
    }
}


@InitiatedBy(protocol = "make-payment-flow")
class FinalizeMakePaymentFlow : ResponderFlow {

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @CordaInject
    lateinit var evmService: EvmService

    /**
     * This function sends the transaction to the EVM for transferring the fractionalized asset
     */
    @Suspendable
    private fun sendEthereumTransaction(inputs: EvmDemoInput): String {

        // Step 1: Build the ethereum transaction
//        val reasonableGasNumber = BigInteger("1388", 16)
        val transactionOptions = TransactionOptions(
            1000000000.toBigInteger(),                 // gasLimit
            0.toBigInteger(),               // value
            20000000000.toBigInteger(),     // maxFeePerGas
            1000000000.toBigInteger(),     // maxPriorityFeePerGas
            inputs.rpcUrl!!,                // rpcUrl
            inputs.buyerAddress,          // from
        )

        // Step 2: Populate the parameters for the transaction on the ERC1155 contract
        val parameters = listOf(
            Parameter.of("from", Type.ADDRESS, inputs.buyerAddress!!),
            Parameter.of("to", Type.ADDRESS, inputs.sellerAddress!!),
            Parameter.of("id", Type.UINT256, 1.toBigInteger()),
            Parameter.of("amount", Type.UINT256, inputs.fractionPurchased!!.toBigInteger()),
            Parameter.of("data", Type.BYTES, ""),
        )

        // Step 3  Call to the Evm to do the asset transfer
        return this.evmService.transaction(
            "safeTransferFrom",
            inputs.contractAddress,
            transactionOptions,
            parameters
        )
    }

    @Suspendable
    override fun call(session: FlowSession) {
        // Receive, verify, validate, sign and record the transaction sent from the initiator
        val receivedMessage = session.receive(EvmDemoInput::class.java)
        session.send(sendEthereumTransaction(receivedMessage))


        utxoLedgerService.receiveFinality(session) {
            // Receive the input details from the initiator

        }
    }
}