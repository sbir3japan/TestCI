package net.corda.flow.application.interop.external.events

import java.math.BigInteger
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.interop.evm.EvmRequest
import net.corda.data.interop.evm.EvmResponse
import net.corda.data.interop.evm.request.GetBlockByHash
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.external.events.factory.ExternalEventRecord
import net.corda.flow.state.FlowCheckpoint
import net.corda.schema.Schemas
import net.corda.v5.application.interop.evm.options.EvmOptions
import org.osgi.service.component.annotations.Component
import net.corda.v5.application.interop.evm.Block;

data class EvmGetBlockByHashParams(
    val options: EvmOptions,
    val hash: String,
    val fullTransactionObjects: Boolean
)

@Component(service = [ExternalEventFactory::class])
class EvmGetBlockByHashExternalEventFactory
    : ExternalEventFactory<EvmGetBlockByHashParams, EvmResponse, Block> {
    override val responseType: Class<EvmResponse> = EvmResponse::class.java

    override fun resumeWith(checkpoint: FlowCheckpoint, response: EvmResponse): Block {

        return (response.payload as net.corda.data.interop.evm.response.Block).toCorda()
    }

    override fun createExternalEvent(
        checkpoint: FlowCheckpoint,
        flowExternalEventContext: ExternalEventContext,
        parameters: EvmGetBlockByHashParams
    ): ExternalEventRecord {
        val block = GetBlockByHash.newBuilder()
            .setBlockHash(parameters.hash)
            .setFullTransactionObjects(parameters.fullTransactionObjects)
            .build()
        val request = EvmRequest.newBuilder()
            .setRpcUrl(parameters.options.rpcUrl)
            .setFrom(parameters.options.from)
            .setTo("")
            .setReturnType(Block::class.java.name)
            .setPayload(block)
            .setFlowExternalEventContext(flowExternalEventContext)
            .build()
        return ExternalEventRecord(
            topic = Schemas.Interop.EVM_REQUEST,
            payload = request
        )
    }

    private fun net.corda.data.interop.evm.response.Block.toCorda(): Block {
        return Block(
            number.toBigInteger(),
            hash,
            parentHash,
            nonce.toBigInteger(),
            sha3Uncles,
            logsBloom,
            transactionsRoot,
            stateRoot,
            receiptsRoot,
            miner,
            "",
            difficulty.toBigInteger(),
            totalDifficulty.toBigInteger(),
            extraData,
            size.toBigInteger(),
            gasLimit.toBigInteger(),
            gasUsed.toBigInteger(),
            timestamp.toBigInteger(),
            transactions,
            uncles,
            0.toBigInteger(),
            maxFeePerGas.toBigInteger(),
            maxPriorityFeePerGas.toBigInteger()
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