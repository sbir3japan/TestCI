package net.corda.flow.pipeline.factory.impl

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.output.FlowStatus
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Flow.FLOW_EVENT_TOPIC
import net.corda.schema.Schemas.Flow.FLOW_STATUS_TOPIC
import org.osgi.service.component.annotations.Component

@Component(service = [FlowRecordFactory::class])
class FlowRecordFactoryImpl : FlowRecordFactory {

    override fun createFlowEventRecord(flowId: String, payload: Any): Record<String, FlowEvent> {
        return Record(
            topic = FLOW_EVENT_TOPIC,
            key = flowId,
            value = FlowEvent(flowId, payload)
        )
    }

    override fun createFlowStatusRecord(status: FlowStatus): Record<FlowKey, FlowStatus> {
        return Record(
            topic = FLOW_STATUS_TOPIC,
            key = status.key,
            value = status
        )
    }

    override fun createFlowMapperEventRecord(key: String, payload: Any): Record<*, FlowMapperEvent> {
        //HARDCODED: Point the process to a custom flow mapper processor deployment
        val flowMapperTopic = "flow.mapper.event"
        return Record(
            topic = flowMapperTopic,
            key = key,
            value = FlowMapperEvent(payload)
        )
    }

    override fun createFlowMapperCleanupRecord(key: String, payload: Any): Record<*, FlowMapperEvent> {
        val flowMapperTopic = System.getenv("FLOW_MAPPER_CLEANUP_TOPIC")
        return Record(
            topic = flowMapperTopic,
            key = key,
            value = FlowMapperEvent(payload)
        )
    }
}