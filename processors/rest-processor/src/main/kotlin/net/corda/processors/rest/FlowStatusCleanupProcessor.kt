package net.corda.processors.rest

import net.corda.data.flow.output.FlowStates
import net.corda.data.rest.ExecuteFlowStatusCleanup
import net.corda.data.rest.FlowStatusRecord
import net.corda.data.scheduler.ScheduledTaskTrigger
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.IntervalFilter
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.Operation
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Rest.REST_FLOW_STATUS_CLEANUP_TOPIC
import net.corda.schema.Schemas.ScheduledTask.SCHEDULE_TASK_NAME_FLOW_STATUS_CLEANUP
import net.corda.schema.configuration.ConfigKeys.REST_FLOW_STATUS_CLEANUP_TIME_MS
import net.corda.utilities.trace
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

class FlowStatusCleanupProcessor(
    config: SmartConfig,
    private val stateManager: StateManager,
    private val now: () -> Instant = Instant::now,
    private val batchSize: Int = BATCH_SIZE,
) : DurableProcessor<String, ScheduledTaskTrigger> {
    companion object {
        private val logger = LoggerFactory.getLogger(FlowStatusCleanupProcessor::class.java)
        private val TERMINAL_STATES = setOf(FlowStates.COMPLETED, FlowStates.FAILED, FlowStates.KILLED)
        private const val FLOW_STATUS_METADATA_KEY = "flowStatus"
        private const val BATCH_SIZE = 500
    }

    override val keyClass: Class<String> = String::class.java
    override val valueClass: Class<ScheduledTaskTrigger> = ScheduledTaskTrigger::class.java
    private val cleanupTimeMilliseconds = config.getLong(REST_FLOW_STATUS_CLEANUP_TIME_MS)

    override fun onNext(events: List<Record<String, ScheduledTaskTrigger>>): List<Record<*, *>> {
        return events.lastOrNull { it.key == SCHEDULE_TASK_NAME_FLOW_STATUS_CLEANUP }?.value?.let { trigger ->
            logger.trace { "Processing flow status cleanup trigger scheduled at ${trigger.timestamp}" }

            getStaleFlowStatuses()
                .map { FlowStatusRecord(it.key, it.value.version) }
                .chunked(batchSize)
                .map { Record(REST_FLOW_STATUS_CLEANUP_TOPIC, UUID.randomUUID(), ExecuteFlowStatusCleanup(it)) }
        } ?: emptyList()
    }

    private fun getStaleFlowStatuses() =
        stateManager.findUpdatedBetweenWithMetadataMatchingAny(
            IntervalFilter(Instant.EPOCH, now().minusMillis(cleanupTimeMilliseconds)),
            TERMINAL_STATES.map { MetadataFilter(FLOW_STATUS_METADATA_KEY, Operation.Equals, it.name) }
        )
}