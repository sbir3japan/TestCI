package net.corda.flow.application.interop.external.events

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.interop.evm.EvmRequest
import net.corda.data.interop.evm.EvmResponse
import net.corda.data.interop.evm.request.GetBalance
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.external.events.factory.ExternalEventRecord
import net.corda.flow.state.FlowCheckpoint
import net.corda.schema.Schemas
import net.corda.v5.application.interop.evm.Parameter
import net.corda.v5.application.interop.evm.options.CallOptions
import net.corda.v5.application.interop.evm.options.EvmOptions
import net.corda.v5.application.marshalling.JsonMarshallingService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.math.BigInteger

data class EvmGetBalanceExternalEventParamaters(
    val options: EvmOptions,
    val address: String,
    val blockNumber: String,
)

@Component(service = [ExternalEventFactory::class])
class EvmGetBalanceExternalEventFactory @Activate constructor(
    @Reference(service = JsonMarshallingService::class)
    private val jsonMarshallingService: JsonMarshallingService
) : ExternalEventFactory< EvmGetBalanceExternalEventParamaters, EvmResponse, BigInteger> {
    override val responseType: Class<EvmResponse> = EvmResponse::class.java

    override fun resumeWith(checkpoint: FlowCheckpoint, response: EvmResponse): BigInteger {
        return response.payload!!.toString().toBigInteger()
    }

    override fun createExternalEvent(
        checkpoint: FlowCheckpoint,
        flowExternalEventContext: ExternalEventContext,
        parameters:  EvmGetBalanceExternalEventParamaters
    ): ExternalEventRecord {
        val call = GetBalance.newBuilder()
            .setAddress(parameters.address)
            .setBlockNumber(parameters.blockNumber)
            .build()

        val request = EvmRequest.newBuilder()
            .setRpcUrl(parameters.options.rpcUrl)
            .setFrom(parameters.options.from ?: "")
            .setTo("")
            .setReturnType(BigInteger::class.java.name)
            .setPayload(call)
            .setFlowExternalEventContext(flowExternalEventContext)
            .build()

        return ExternalEventRecord(
            topic = Schemas.Interop.EVM_REQUEST,
            payload = request
        )
    }

    private fun CallOptions.toAvro(): net.corda.data.interop.evm.request.CallOptions {
        return net.corda.data.interop.evm.request.CallOptions(blockNumber)
    }

    private fun List<Parameter<*>>.toAvro() = map { it.toAvro() }

    private fun Parameter<*>.toAvro(): net.corda.data.interop.evm.request.Parameter {
        return net.corda.data.interop.evm.request.Parameter(name, type.name, jsonMarshallingService.format(value))
    }
}