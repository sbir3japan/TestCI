package com.r3.corda.demo.swaps.workflows.swap

import com.r3.corda.demo.swaps.workflows.internal.DraftTxService
import net.corda.v5.application.crypto.SignatureSpecService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.flows.*
import net.corda.v5.application.interop.evm.EvmService
import net.corda.v5.application.interop.evm.options.EvmOptions
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigitalSignature
import org.utils.Numeric

data class RequestBlockHeaderProofsFlowInput(
    val blockNumber: Int,
    val validators: List<String>,
    val rpcUrl: String
)

data class RequestBlockHeaderProofsFlowOutput(
    val signatures: List<DigitalSignature.WithKeyId>
)

/**
 * Initiating flow which requests validation and attestation of an EVM event from a pool of approved validators.
 */
@Suppress("unused")
@InitiatingFlow(protocol = "com.r3.corda.demo.swaps.workflows.swap.RequestBlockHeaderProofsFlow")
class RequestBlockHeaderProofsFlow : ClientStartableFlow {

    @CordaInject
    lateinit var signingService: SigningService

    @CordaInject
    lateinit var signatureSpecService: SignatureSpecService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var evmService: EvmService

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var persistenceService: PersistenceService

    @CordaInject
    lateinit var serializationService: SerializationService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {

        val (blockNumber, validators, rpcUrl) =
            requestBody.getRequestBodyAs(
                jsonMarshallingService,
                RequestBlockHeaderProofsFlowInput::class.java
            )

        val myKey = memberLookup.myInfo().ledgerKeys.first()

        val signatures = validators
            .map { validator -> MemberX500Name.parse(validator) }
            .map { validator ->
                val validatorInfo = memberLookup.lookup(validator) ?: throw IllegalArgumentException("Could not resolve ${validator.commonName}.")
                if(validatorInfo.ledgerKeys.first() == myKey) {
                    signReceiptRoot(evmService, signingService, signatureSpecService, memberLookup, rpcUrl, blockNumber)
                } else {
                    val session = flowMessaging.initiateFlow(validator)
                    session.send(rpcUrl)
                    session.send(blockNumber)
                    session.receive(DigitalSignature.WithKeyId::class.java)
                }
        }

        val draftTxService = DraftTxService(persistenceService, serializationService)

        draftTxService.saveBlockSignatures(blockNumber.toBigInteger(), signatures)

        return jsonMarshallingService.format(RequestBlockHeaderProofsFlowOutput(signatures))
    }
}

/**
 * Responder flow which validates and attests (signs) an EVM event
 */
@InitiatedBy(protocol = "com.r3.corda.demo.swaps.workflows.swap.RequestBlockHeaderProofsFlow")
class RequestBlockHeaderProofsFlowResponder : ResponderFlow {

    @CordaInject
    lateinit var signingService: SigningService

    @CordaInject
    lateinit var signatureSpecService: SignatureSpecService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var evmService: EvmService

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @Suspendable
    override fun call(session: FlowSession) {
        val rpcUrl = session.receive(String::class.java)
        val blockNumber = session.receive(Int::class.java)

        val signatures = signReceiptRoot(
            evmService,
            signingService,
            signatureSpecService,
            memberLookup,
            rpcUrl,
            blockNumber
        )

        session.send(signatures)
    }
}

@Suspendable
fun Flow.signReceiptRoot(
    evmService: EvmService,
    signingService: SigningService,
    signatureSpecService: SignatureSpecService,
    memberLookup: MemberLookup,
    rpcUrl: String,
    blockNumber: Int
) : DigitalSignature.WithKeyId {

    val block = evmService.getBlockByNumber(
        blockNumber.toBigInteger(),
        false,
        EvmOptions(rpcUrl,"")
    )

    val receiptsRootHash = Numeric.hexStringToByteArray(block.receiptsRoot)

    val myKey = memberLookup.myInfo().ledgerKeys.first()

    return signingService.sign(receiptsRootHash, myKey, signatureSpecService.defaultSignatureSpec(myKey)!!)
}
