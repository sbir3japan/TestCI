package net.corda.flow.session.impl

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.flow.state.session.SessionState
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import org.slf4j.LoggerFactory

class StateManagerHelper(
    private val stateManager: StateManager,
    private val avroSerializer: CordaAvroSerializer<Any>,
    private val avroDeserializer: CordaAvroDeserializer<Any>
) {
    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    fun createSessionStates(states: Collection<SessionState>) {
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
        var states = stateManager.get(ids).also {
            if (it.size != ids.size) {
                throw IllegalArgumentException("Failed to find all required states. Missing: ${ids.toSet() - it.keys}")
            }
        }
        do {
            val newStates = states.mapValues {
                val state = avroDeserializer.deserialize(it.value.value) as? SessionState
                    ?: throw IllegalArgumentException("Failed to deserialize state")
                val newSession =
                    updateFns[it.key]?.invoke(state) ?: throw IllegalArgumentException("Missing update function")
                val serialized =
                    avroSerializer.serialize(newSession) ?: throw IllegalArgumentException("Could not serialize state")
                State(
                    it.value.key,
                    serialized,
                    version = it.value.version + 1,
                    metadata = it.value.metadata
                )
            }
            states = stateManager.update(newStates.values)
        } while (states.isNotEmpty())
    }
}