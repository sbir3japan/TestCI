package net.corda.flow.application.persistence.external.events

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.persistence.EntityRequest
import net.corda.data.persistence.EntityResponse
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.external.events.factory.ExternalEventRecord
import net.corda.flow.state.FlowCheckpoint
import net.corda.schema.Schemas
import net.corda.virtualnode.toAvro
import java.nio.ByteBuffer

abstract class AbstractPersistenceExternalEventFactory<PARAMETERS : Any> :
    ExternalEventFactory<PARAMETERS, EntityResponse, EntityResponseDto> {

    abstract fun createRequest(parameters: PARAMETERS): Any

    override val responseType = EntityResponse::class.java

    override fun createExternalEvent(
        checkpoint: FlowCheckpoint,
        flowExternalEventContext: ExternalEventContext,
        parameters: PARAMETERS
    ): ExternalEventRecord {
        return ExternalEventRecord(
            topic = Schemas.Persistence.PERSISTENCE_ENTITY_PROCESSOR_TOPIC,
            payload = EntityRequest.newBuilder()
                .setHoldingIdentity(checkpoint.holdingIdentity.toAvro())
                .setRequest(createRequest(parameters))
                .setFlowExternalEventContext(flowExternalEventContext)
                .build()
        )
    }

    override fun resumeWith(checkpoint: FlowCheckpoint, response: EntityResponse): EntityResponseDto {
        return EntityResponseDto(
            response.newOffset,
            response.hasNextPage,
            response.size,
            response.results
        )
    }
}

data class EntityResponseDto(
    val newOffset: Int,
    val hasNextPage: Boolean,
    val size: Int,
    val results: List<ByteBuffer>
)
