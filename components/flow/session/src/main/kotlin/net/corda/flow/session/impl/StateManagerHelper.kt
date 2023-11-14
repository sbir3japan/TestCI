package net.corda.flow.session.impl

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.flow.state.session.SessionState
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

class StateManagerHelper(
    private val stateManager: StateManager,
    private val avroSerializer: CordaAvroSerializer<Any>,
    private val avroDeserializer: CordaAvroDeserializer<Any>
) : AutoCloseable {
    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    fun createSessionStates(states: Collection<SessionState>) {
        logger.info("Creating session states with ids: ${states.joinToString { it.sessionId }}")
        val stateManagerStates = states.map {
            val serialized = avroSerializer.serialize(it)
                ?: throw IllegalArgumentException("Could not serialize session state")
            State(
                it.sessionId,
                serialized
            )
        }
        stateManager.create(stateManagerStates).let { failed ->
            if (failed.isNotEmpty()) {
                logger.warn("Failed to create new sessions for IDs ${failed.keys}")
                throw IllegalArgumentException("Failed to create new sessions for IDs ${failed.keys}")
            }
        }
    }

    fun updateSessionStates(updateFns: Map<String, (SessionState) -> SessionState>) {
        val ids = updateFns.keys
        val time = Instant.now()
        logger.info("Updating sessions with IDs $ids")
        var states = retrieveStates(ids)
        do {
            val newStates = states.mapNotNull {
                val state = avroDeserializer.deserialize(it.value.value) as? SessionState
                    ?: throw IllegalArgumentException("Failed to deserialize state")
                val newSession = try {
                    updateFns[it.key]?.invoke(state) ?: throw IllegalArgumentException("Missing update function")
                } catch (e: RetryException) {
                    return@mapNotNull null
                }
                val serialized =
                    avroSerializer.serialize(newSession) ?: throw IllegalArgumentException("Could not serialize state")
                it.key to State(
                    it.value.key,
                    serialized,
                    version = it.value.version,
                    metadata = it.value.metadata
                )
            }.toMap()
            val failedStates = if (newStates.isNotEmpty()) {
                stateManager.update(newStates.values)
            } else {
                emptyMap()
            }
            val retryingStateKeys = states.keys - newStates.keys
            val retryingStates = retrieveStates(retryingStateKeys)
            states = failedStates + retryingStates
        } while (states.isNotEmpty() && time + Duration.ofMillis(10000) > Instant.now())
        if (states.isNotEmpty()) {
            logger.warn("Failed to update some states in time limit: ${states.keys}")
            throw IllegalStateException("Failed to update some states in time limit: ${states.keys}")
        }
    }

    private fun retrieveStates(ids: Collection<String>) : Map<String, State> {
        return if (ids.isNotEmpty()) {
            stateManager.get(ids).also {
                if (it.size != ids.size) {
                    throw IllegalArgumentException("Failed to find all required states. Missing: ${ids.toSet() - it.keys}")
                }
            }
        } else {
            emptyMap()
        }
    }

    private fun maybeGetStates(ids: Collection<String>) : Map<String, State> {
        return if (ids.isNotEmpty()) {
            stateManager.get(ids)
        } else {
            emptyMap()
        }
    }

    fun getStates(ids: Collection<String>) : Map<String, SessionState> {
        return maybeGetStates(ids).mapValues {
            avroDeserializer.deserialize(it.value.value) as? SessionState
                ?: throw IllegalArgumentException("Failed to deserialize state")
        }
    }

    fun deleteStates(ids: Collection<String>) {
        val states = retrieveStates(ids)
        val failed = stateManager.delete(states.values)
        if (failed.isNotEmpty()) {
            throw IllegalArgumentException("Failed to delete states: ${failed.keys}")
        }
    }

    override fun close() {
        stateManager.stop()
    }
}