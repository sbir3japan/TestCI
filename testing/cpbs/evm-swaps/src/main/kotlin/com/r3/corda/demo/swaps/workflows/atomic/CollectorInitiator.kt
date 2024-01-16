package com.r3.corda.demo.swaps.workflows.atomic

import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.interop.evm.Block
import net.corda.v5.application.interop.evm.EvmService
import net.corda.v5.application.interop.evm.options.EvmOptions
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec
import org.utils.Numeric
import java.math.BigInteger
import java.security.PublicKey

object Keccak256SignatureSpec : SignatureSpec {
    override fun getSignatureName(): String {
        return "Keccak256"
    }
}

@Suspendable
@InitiatedBy(protocol = "collect-block-signatures-responder-flow")
@InitiatingFlow(protocol = "collect-initiator")
class CollectInitiator : ResponderFlow {


    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var signingService: SigningService

    @CordaInject
    lateinit var evmService: EvmService

    @Suspendable
    override fun call(session: FlowSession) {
        val request = session.receive(CollectBlockSignaturesParams::class.java)
        val signature = signReceiptRoot(request.blockNumber!!, memberLookup.myInfo().ledgerKeys.first())

        flowMessaging.initiateFlow(request.recipient).send(signature)

        if (request.blocking == true) {
            session.send(true)
        }

    }


    @Suspendable
    fun getBlock(blockNumber: BigInteger): Block {
        return evmService.getBlockByNumber(blockNumber, true, EvmOptions("http://127.0.0.1:9944", ""))
    }

    @Suspendable
    fun signReceiptRoot(blockNumber: BigInteger, key: PublicKey): DigitalSignature.WithKeyId {
        val block = getBlock(blockNumber)
        val receiptsRootHash = Numeric.hexStringToByteArray(block.receiptsRoot)
        return signingService.sign(receiptsRootHash, key, Keccak256SignatureSpec)

    }
}
