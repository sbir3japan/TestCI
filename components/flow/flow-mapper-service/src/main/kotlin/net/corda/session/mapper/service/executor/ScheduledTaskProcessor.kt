package net.corda.session.mapper.service.executor

import net.corda.data.scheduler.ScheduledTaskTrigger
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import org.slf4j.LoggerFactory
import java.time.Clock

@Suppress("unused_parameter")
class ScheduledTaskProcessor(
    private val stateManager: StateManager,
    private val clock: Clock,
    private val cleanupWindow: Long,
    private val batchSize: Int = ID_BATCH_SIZE
): DurableProcessor<String, ScheduledTaskTrigger> {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val ID_BATCH_SIZE = 200
    }
    override fun onNext(events: List<Record<String, ScheduledTaskTrigger>>): List<Record<*, *>> {
        if (events.any { it.value?.name == Schemas.ScheduledTask.SCHEDULED_TASK_NAME_MAPPER_CLEANUP }) {
            stateManager.deleteExpired()
        }
        return listOf()
    }

    override val keyClass = String::class.java
    override val valueClass = ScheduledTaskTrigger::class.java
}
