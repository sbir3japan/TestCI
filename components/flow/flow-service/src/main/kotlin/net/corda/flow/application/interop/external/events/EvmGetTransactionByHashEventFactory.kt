package net.corda.flow.application.interop.external.events

import java.math.BigInteger
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.interop.evm.EvmRequest
import net.corda.data.interop.evm.EvmResponse
import net.corda.data.interop.evm.request.GetBlockByHash
import net.corda.data.interop.evm.request.GetTransactionByHash
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.external.events.factory.ExternalEventRecord
import net.corda.flow.state.FlowCheckpoint
import net.corda.schema.Schemas
import net.corda.v5.application.interop.evm.options.EvmOptions
import org.osgi.service.component.annotations.Component
import net.corda.v5.application.interop.evm.Block;
import net.corda.v5.application.interop.evm.TransactionObject
import net.corda.v5.application.interop.evm.options.TransactionOptions

data class EvmGetTransactionByHashEventFactoryParams(
    val options: EvmOptions,
    val hash: String
)

@Component(service = [ExternalEventFactory::class])
class EvmGetTransactionByHashExternalEventFactory
    : ExternalEventFactory<EvmGetTransactionByHashEventFactoryParams, EvmResponse, TransactionObject> {
    override val responseType: Class<EvmResponse> = EvmResponse::class.java

    override fun resumeWith(checkpoint: FlowCheckpoint, response: EvmResponse): TransactionObject {
        return (response.payload as net.corda.data.interop.evm.response.TransactionObject).toCorda()
    }

    override fun createExternalEvent(
        checkpoint: FlowCheckpoint,
        flowExternalEventContext: ExternalEventContext,
        parameters: EvmGetTransactionByHashEventFactoryParams
    ): ExternalEventRecord {
        val transaction = GetTransactionByHash.newBuilder()
            .setHash(parameters.hash)
            .build()
        val request = EvmRequest.newBuilder()
            .setRpcUrl(parameters.options.rpcUrl)
            .setFrom(parameters.options.from)
            .setTo("")
            .setReturnType(Block::class.java.name)
            .setPayload(transaction)
            .setFlowExternalEventContext(flowExternalEventContext)
            .build()
        return ExternalEventRecord(
            topic = Schemas.Interop.EVM_REQUEST,
            payload = request
        )
    }

    // TODO: Correct type for TransactionObject
    private fun net.corda.data.interop.evm.response.TransactionObject.toCorda(): TransactionObject {
        return TransactionObject(
            blockHash,
            blockNumber.toBigInteger(),
            from,
            gas.toBigInteger(),
            gasPrice.toBigInteger(),
            maxFeePerGas.toBigInteger(),
            maxPriorityFeePerGas.toBigInteger(),
            hash,
            input,
            nonce.toBigInteger(),
            to,
            transactionIndex.toBigInteger(),
            value.toBigInteger(),
            "",
            v,
            r,
            s
        )
    }

    //
    private fun String?.toBigInteger(): BigInteger {
        return if (isNullOrEmpty()) {
            BigInteger.ZERO
        } else if (startsWith("0x")) {
            BigInteger(this.substring(2), 16)
        } else {
            BigInteger(this, 16)
        }
    }



}