package net.corda.flow.scheduler.impl

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.flow.scheduler.FlowWakeUpScheduler
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.metrics.CordaMetrics
import net.corda.schema.Schemas.Flow.FLOW_EVENT_TOPIC
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.virtualnode.toCorda
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowWakeUpScheduler::class])
class FlowWakeUpSchedulerImpl constructor(
    private val publisherFactory: PublisherFactory,
    private val flowRecordFactory: FlowRecordFactory,
    private val scheduledExecutorService: ScheduledExecutorService
) : FlowWakeUpScheduler {

    @Activate
    constructor(
        @Reference(service = PublisherFactory::class)
        publisherFactory: PublisherFactory,
        @Reference(service = FlowRecordFactory::class)
        flowRecordFactory: FlowRecordFactory,
    ) : this(publisherFactory, flowRecordFactory, Executors.newSingleThreadScheduledExecutor())

    private val scheduledWakeUps = ConcurrentHashMap<String, ScheduledFuture<*>>()
    private var publisher: Publisher? = null

    override fun onConfigChange(config: Map<String, SmartConfig>) {
        publisher?.close()
        publisher = publisherFactory.createPublisher(
            PublisherConfig("FlowWakeUpRestResource", topic = FLOW_EVENT_TOPIC),
            config.getConfig(MESSAGING_CONFIG)
        )
    }

    override fun onPartitionSynced(states: Map<String, Checkpoint>) {
        scheduleTasks(states.values)
    }

    override fun onPartitionLost(states: Map<String, Checkpoint>) {
        cancelScheduledWakeUps(states.keys)
    }

    override fun onPostCommit(updatedStates: Map<String, Checkpoint?>) {
        val updates = updatedStates.filter { it.value != null }.map { it.value!! }
        val deletes = updatedStates.filter { it.value == null }

        scheduleTasks(updates)
        cancelScheduledWakeUps(deletes.keys)
    }

    private fun scheduleTasks(checkpoints: Collection<Checkpoint>) {
        checkpoints.forEach {
            val id = it.flowId
            val holdingIdShortHash = it.flowState?.flowStartContext?.identity?.toCorda()?.shortHash?.toString()
            val scheduledWakeUp = scheduledExecutorService.schedule(
                { publishWakeUp(id, holdingIdShortHash) },
                it.pipelineState.maxFlowSleepDuration.toLong(),
                TimeUnit.MILLISECONDS
            )

            val existingWakeUp = scheduledWakeUps.put(id, scheduledWakeUp)
            existingWakeUp?.cancel(false)
        }
    }

    private fun cancelScheduledWakeUps(flowIds:Collection<String>){
        flowIds.forEach {
            scheduledWakeUps.remove(it)?.cancel(false)
        }
    }

    private fun publishWakeUp(flowId: String, holdingIdentity: String?) {
        // There appears to be a condition where the flow start context is nulled out (or has some nulled fields). Check
        // this to prevent an issue where attempting to record a metric results in taking down the state and event
        // pattern.
        if (holdingIdentity != null) {
            CordaMetrics.Metric.FlowScheduledWakeupCount.builder()
                .forVirtualNode(holdingIdentity)
                .build().increment()
        }
        publisher?.publish(listOf(flowRecordFactory.createFlowEventRecord(flowId, Wakeup())))
    }
}