package net.corda.libs.statemanager.impl

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.Row
import com.datastax.oss.driver.api.core.data.ByteUtils
import com.datastax.oss.driver.api.core.type.codec.ExtraTypeCodecs
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import net.corda.libs.statemanager.api.IntervalFilter
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.StateOperationGroup
import net.corda.libs.statemanager.impl.lifecycle.CheckConnectionEventHandler
import net.corda.libs.statemanager.impl.metrics.MetricsRecorder
import net.corda.libs.statemanager.impl.metrics.MetricsRecorder.OperationType.GET
import net.corda.libs.statemanager.impl.model.v1.StateColumns
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

@Suppress("TooManyFunctions")
class ScyllaStateManager(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    private val session: CqlSession,
    private val metricsRecorder: MetricsRecorder,
) : StateManager {
    override val name = LifecycleCoordinatorName(
        "StateManager",
        UUID.randomUUID().toString()
    )
    private val eventHandler = CheckConnectionEventHandler(name) { session.isClosed }
    private val lifecycleCoordinator = lifecycleCoordinatorFactory.createCoordinator(name, eventHandler)

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private val objectMapper = ObjectMapper()
    }

    /**
     * Internal method to retrieve states by key without recording any metrics.
     */
    private fun getByKey(keys: Collection<String>): Map<String, State> {
        if (keys.isEmpty()) return emptyMap()

        return session.execute("""
            SELECT key, value, metadata, version, 
            modified_time FROM state 
            WHERE key IN (${keys.map { "'$it'" }.joinToString(",") });
        """.trimIndent()).map {
            resultRowAsState(it)
        }.associateBy {
            it.key
        }
    }

    fun resultRowAsState(row: Row): State {
            val key = row.getString(StateColumns.KEY_COLUMN)!!
            val value = ByteUtils.getArray(row.getByteBuffer(StateColumns.VALUE_COLUMN))
            val metadata = row.getString(StateColumns.METADATA_COLUMN)!!
            val version = row.getInt(StateColumns.VERSION_COLUMN)
            val modifiedTime = row.getInstant(StateColumns.MODIFIED_TIME_COLUMN)!!

            return State(key, value, version, Metadata(objectMapper.readValue(metadata)), modifiedTime)
    }

    override fun create(states: Collection<State>): Set<String> {
        if (states.isEmpty()) return emptySet()
        val duplicateStatesKeys = states.groupBy {
            it.key
        }.filter {
            it.value.size > 1
        }.keys
        if (duplicateStatesKeys.isNotEmpty()) {
            throw IllegalArgumentException(
                "Creating multiple states with the same key is not supported," +
                    " duplicated keys found: $duplicateStatesKeys"
            )
        }
        return metricsRecorder.recordProcessingTime(MetricsRecorder.OperationType.CREATE) {
            // NOTE: not using batching as we probably don't need to and so we can identify failed inserts
            val failedInserts = mutableSetOf<String>()
            val stmt = session.prepare("INSERT INTO state(key, value, version, metadata, modified_time) VALUES(?,?,?,?,?) IF NOT EXISTS")
            states.map { state ->
                stmt.bind()
                    .setString(0, state.key)
                    .set(1, state.value,  ExtraTypeCodecs.BLOB_TO_ARRAY)
                    .setInt(2, state.version)
                    .setString(3, objectMapper.writeValueAsString(state.metadata))
                    .setInstant(4, Instant.now()).also {
                        if(!session.execute(it).wasApplied()) failedInserts.add(state.key)
                    }
            }
            failedInserts
        }.also {
            if (it.isNotEmpty()) {
                metricsRecorder.recordFailureCount(MetricsRecorder.OperationType.CREATE, it.size)
            }
        }
    }

    override fun get(keys: Collection<String>): Map<String, State> {
        if (keys.isEmpty()) return emptyMap()

        return metricsRecorder.recordProcessingTime(GET) {
            getByKey(keys)
        }
    }

    override fun update(states: Collection<State>): Map<String, State?> {
        if (states.isEmpty()) return emptyMap()

        return metricsRecorder.recordProcessingTime(MetricsRecorder.OperationType.UPDATE) {
            // NOTE: not using batching as we probably don't need to and so we can identify failed updates
            val failedUpdates = mutableMapOf<String, State?>()
            val stmt = session.prepare("""
                UPDATE state SET 
                    value = ?, 
                    metadata = ?, 
                    modified_time = ?,
                    version = ?
                WHERE key = ? IF version = ?;
            """.trimIndent())
            states.map { state ->
                stmt.bind()
                    .set(0, state.value,  ExtraTypeCodecs.BLOB_TO_ARRAY)
                    .setString(1, objectMapper.writeValueAsString(state.metadata))
                    .setInstant(2, Instant.now())
                    .setInt(3, state.version + 1)
                    .setString(4, state.key)
                    .setInt(5, state.version)
                    .also {
                        if(!session.execute(it).wasApplied()) failedUpdates[state.key] = state
                    }
            }
            failedUpdates
        }.also {
            if (it.isNotEmpty()) {
                metricsRecorder.recordFailureCount(MetricsRecorder.OperationType.CREATE, it.size)
            }
        }
    }

    override fun delete(states: Collection<State>): Map<String, State> {
        if (states.isEmpty()) return emptyMap()
        return metricsRecorder.recordProcessingTime(MetricsRecorder.OperationType.DELETE) {
            val failedDeletes = mutableMapOf<String, State>()
            try {
                val stmt = session.prepare("""
                DELETE FROM state WHERE key = ? IF version = ?;
            """.trimIndent())
                states.map { state ->
                    stmt.bind()
                        .setString(0, state.key)
                        .setInt(1, state.version)
                        .also {
                            if(!session.execute(it).wasApplied()) {
                                failedDeletes[state.key] = state
                            }
                        }
                }
            } catch (e: Exception) {
                logger.warn("Failed to delete batch of states - ${states.joinToString { it.key }}", e)
                throw e
            }
            failedDeletes.also {
                if (failedDeletes.isNotEmpty()) {
                    metricsRecorder.recordFailureCount(MetricsRecorder.OperationType.DELETE, it.size)
                    logger.warn(
                        "Optimistic locking check failed while deleting States" +
                                " ${failedDeletes.keys.joinToString()}"
                    )
                }
            }
        }
    }

    override fun createOperationGroup(): StateOperationGroup {
        // NOT USED!!
//        return StateOperationGroupImpl(dataSource, stateRepository)
        return object : StateOperationGroup {
            override fun create(states: Collection<State>): StateOperationGroup {
                TODO("Not yet implemented")
            }

            override fun update(states: Collection<State>): StateOperationGroup {
                TODO("Not yet implemented")
            }

            override fun delete(states: Collection<State>): StateOperationGroup {
                TODO("Not yet implemented")
            }

            override fun execute(): Map<String, State?> {
                TODO("Not yet implemented")
            }

        }
    }

    override fun updatedBetween(interval: IntervalFilter): Map<String, State> {
        // NOTE USED!!
        TODO("Not yet implemented")
//        return metricsRecorder.recordProcessingTime(FIND) {
//            dataSource.connection.use { connection ->
//                stateRepository.updatedBetween(connection, interval)
//            }.associateBy {
//                it.key
//            }
//        }
    }

    override fun findByMetadataMatchingAll(filters: Collection<MetadataFilter>): Map<String, State> {
        if (filters.isEmpty()) return emptyMap()

        // NOT working - can't filter in json doc --> needs scenario specific schemas.
        //  not used in critical flow path
//        return metricsRecorder.recordProcessingTime(MetricsRecorder.OperationType.FIND) {
//            session.execute(
//                """
//            SELECT key, value, fromJson(metadata), version, modified_time
//            FROM state
//
//        """.trimIndent()
//            ).map {
//                resultRowAsState(it)
//            } .associateBy {
//                it.key
//            }
//        }

        return emptyMap()
    }

    override fun findByMetadataMatchingAny(filters: Collection<MetadataFilter>): Map<String, State> {
        if (filters.isEmpty()) return emptyMap()

//        return metricsRecorder.recordProcessingTime(FIND) {
//            dataSource.connection.use { connection ->
//                stateRepository.filterByAny(connection, filters)
//            }.associateBy {
//                it.key
//            }
//        }
        return emptyMap()
    }

    override fun findUpdatedBetweenWithMetadataMatchingAll(
        intervalFilter: IntervalFilter,
        metadataFilters: Collection<MetadataFilter>
    ): Map<String, State> {
//        return metricsRecorder.recordProcessingTime(FIND) {
//            dataSource.connection.use { connection ->
//                stateRepository.filterByUpdatedBetweenWithMetadataMatchingAll(
//                    connection,
//                    intervalFilter,
//                    metadataFilters
//                )
//            }.associateBy {
//                it.key
//            }
//        }
        return emptyMap()
    }

    override fun findUpdatedBetweenWithMetadataMatchingAny(
        intervalFilter: IntervalFilter,
        metadataFilters: Collection<MetadataFilter>
    ): Map<String, State> {
//        return metricsRecorder.recordProcessingTime(FIND) {
//            dataSource.connection.use { connection ->
//                stateRepository.filterByUpdatedBetweenWithMetadataMatchingAny(
//                    connection,
//                    intervalFilter,
//                    metadataFilters
//                )
//            }.associateBy {
//                it.key
//            }
//        }
        return emptyMap()
    }

    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning

    override fun start() {
        lifecycleCoordinator.start()
    }

    override fun stop() {
        lifecycleCoordinator.close()
    }
}
