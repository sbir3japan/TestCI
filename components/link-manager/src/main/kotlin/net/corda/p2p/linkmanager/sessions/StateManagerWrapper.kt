package net.corda.p2p.linkmanager.sessions

import net.corda.data.p2p.event.SessionDirection
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.metrics.CordaMetrics
import net.corda.p2p.linkmanager.metrics.recordP2PMetric
import net.corda.p2p.linkmanager.metrics.recordSessionCreationTime
import net.corda.p2p.linkmanager.sessions.metadata.OutboundSessionMetadata.Companion.toOutbound
import net.corda.p2p.linkmanager.sessions.metadata.OutboundSessionStatus
import net.corda.p2p.linkmanager.state.direction
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class StateManagerWrapper(
    private val stateManager: StateManager,
    private val sessionCache: SessionCache,
) {
    private companion object {
        val logger: Logger = LoggerFactory.getLogger(StateManagerWrapper::class.java)
    }
    fun get(
        keys: Collection<String>,
    ) = sessionCache.validateStatesAndScheduleExpiry(
        stateManager.get(keys),
    )

    fun findStatesMatchingAny(
        filters: Collection<MetadataFilter>,
    ) = sessionCache.validateStatesAndScheduleExpiry(
        stateManager.findByMetadataMatchingAny(filters),
    )

    fun upsert(
        changes: Collection<StateManagerAction>,
    ): Map<String, State?> {
        val updateActions = changes.filterIsInstance<UpdateAction>()
        val updates = updateActions
            .map {
                it.state
            }.mapNotNull {
                sessionCache.validateStateAndScheduleExpiry(
                    state = it,
                    beforeUpdate = true,
                )
            }
        val creates = changes.filterIsInstance<CreateAction>()
            .map {
                it.state
            }.mapNotNull {
                sessionCache.validateStateAndScheduleExpiry(it)
            }
        val failedUpdates = if (updates.isNotEmpty()) {
            stateManager.update(updates).onEach {
                logger.info("Failed to update the state of session with ID ${it.key}")
            }
        } else {
            emptyMap()
        }
        recordSessionUpdateMetrics((updateActions.associateBy { it.state.key } - failedUpdates.keys).values)
        val failedCreates = if (creates.isNotEmpty()) {
            stateManager.create(creates).associateWith {
                logger.info("Failed to create the state of session with ID $it")
                null
            }
        } else {
            emptyMap()
        }
        recordSessionStartMetrics((creates.associateBy { it.key } - failedCreates.keys).values)
        return failedUpdates + failedCreates
    }

    private fun recordSessionStartMetrics(creates: Collection<State>) {
        creates.groupBy { it.direction() }.forEach {
            recordP2PMetric(CordaMetrics.Metric.SessionStartedCount, it.key, it.value.size.toDouble())
        }
    }

    private fun recordSessionUpdateMetrics(updates: Collection<UpdateAction>) {
        updates.forEach {
            val direction = it.state.direction()
            if (it.isReplay) {
                recordP2PMetric(CordaMetrics.Metric.SessionMessageReplayCount, direction)
            }
            if (direction == SessionDirection.OUTBOUND) {
                val outbound = it.state.metadata.toOutbound()
                if (outbound.status == OutboundSessionStatus.SessionReady) {
                    recordP2PMetric(CordaMetrics.Metric.SessionEstablishedCount, direction)
                    recordSessionCreationTime(outbound.initiationTimestamp)
                }
            }
        }
    }
}
