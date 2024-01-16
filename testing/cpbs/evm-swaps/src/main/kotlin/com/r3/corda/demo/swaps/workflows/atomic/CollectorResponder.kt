package com.r3.corda.demo.swaps.workflows.atomic

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.interop.evm.EvmService
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.DigitalSignature
import java.math.BigInteger

class RequestParamsWithSignature(
    val blockNumber: BigInteger?,
    val blocking: Boolean?,
    val signature: DigitalSignature.WithKeyId
)


@Suspendable
@InitiatedBy(protocol = "collect-initiator")
class CollectorResponder : ResponderFlow {

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var evmService: EvmService

    @Suspendable
    override fun call(session: FlowSession) {
        val request = session.receive(RequestParamsWithSignature::class.java)
        //DraftTxService.saveBlockSignature(request.blockNumber!!, request.signature)
        
        if (request.blocking == true) {
            session.send(true)
        }
    }
}
