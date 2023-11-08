package net.corda.flow.session.impl

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.session.SessionProcessState
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.flow.session.SessionManager
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Flow.FLOW_EVENT_TOPIC
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.toCorda
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

// For this prototype, assume all virtual nodes are local.
class SessionManagerImpl(
    private val stateManager: StateManager,
    private val avroSerializer: CordaAvroSerializer<Any>,
    private val avroDeserializer: CordaAvroDeserializer<Any>,
    private val publisher: Publisher
) : SessionManager {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val INITIATED_SUFFIX = "-INITIATED"
    }

    // Only handles local counterparties.
    override fun createSession(flowID: String, config: SessionManager.SessionConfig): String {
        // TODO: Need to cope with replays
        val sessionID = UUID.randomUUID().toString()
        val counterpartySessionID = sessionID + INITIATED_SUFFIX
        val createdTime = Instant.now()
        val initiatorState = createNewState(
            sessionID,
            createdTime,
            config.counterparty,
            config.requireClose,
            config.contextSessionProperties
        )
        val initiatedState = createNewState(
            counterpartySessionID,
            createdTime,
            config.party,
            config.requireClose,
            config.contextSessionProperties
        )
        // Publish a record to start the counterparty flow.
        val initPayload = SessionInit.newBuilder()
            .setCpiId(config.cpiId)
            .setContextPlatformProperties(null)
            .setContextUserProperties(null)
            .setFlowId(null)
            .build()
        val initEvent = generateSessionEvent(byteArrayOf(), initiatorState, config.party, config.counterparty, initPayload)
        initiatedState.receivedEventsState.lastProcessedSequenceNum = 1
        // Set up sessions.
        val failedStates = stateManager.create(listOf(
            State(
                sessionID,
                avroSerializer.serialize(initiatorState)
                    ?: throw IllegalArgumentException("Failed to serialize initiator state")
            ),
            State(
                counterpartySessionID,
                avroSerializer.serialize(initiatedState)
                    ?: throw IllegalArgumentException("Failed to serialize initiated state")
            )
        ))
        if (failedStates.isNotEmpty()) {
            throw IllegalArgumentException("Failed to store new session states")
        }
        publisher.publish(listOf(Record(FLOW_EVENT_TOPIC, counterpartySessionID, FlowEvent(counterpartySessionID, initEvent))))
        return sessionID
    }

    override fun sendMessage(sessionID: String, message: ByteArray) {
        val receivingID = toggleSessionID(sessionID)
        val requiredIDs = setOf(sessionID, receivingID)
        val states = stateManager.get(requiredIDs)
        if (requiredIDs.any { !states.containsKey(it) }) {
            throw IllegalArgumentException("Could not find required session state. Ids: ${requiredIDs.joinToString(",")}")
        }
        val deserializedStates = states.map {
            val sessionState = avroDeserializer.deserialize(it.value.value) as? SessionState
                ?: throw IllegalArgumentException("Could not deserialize session state for ${it.key}")
            it.key to sessionState
        }.toMap()
        val (initiatingIdentity, initiatedIdentity) = calculateInitiatingAndInitiated(deserializedStates)
        val sessionEvent = generateSessionEvent(
            message,
            deserializedStates[sessionID]!!,
            initiatingIdentity,
            initiatedIdentity
        )
        val receivedState = deserializedStates[receivingID]!!.receivedEventsState
        receivedState.apply {
            undeliveredMessages = undeliveredMessages + sessionEvent
        }
        deserializedStates[receivingID]!!.receivedEventsState = receivedState
        val statesToWrite = states.map {
            val updatedSessionState = deserializedStates[it.key]!!
            val serializedState = avroSerializer.serialize(updatedSessionState)!!
            State(
                it.value.key,
                serializedState,
                version = it.value.version + 1,
                metadata = it.value.metadata
            )
        }
        val failed = stateManager.update(statesToWrite)
        if (failed.isNotEmpty()) {
            throw IllegalArgumentException("Failed to write some states after send: ${failed.keys}")
        }
    }

    override fun receiveMessage(sessionID: String): ByteArray {
        TODO("Not yet implemented")
    }

    override fun deleteSession(sessionID: String) {
        TODO("Not yet implemented")
    }

    private fun createNewState(
        sessionID: String,
        createTime: Instant,
        counterparty: HoldingIdentity,
        requireClose: Boolean,
        sessionProperties: KeyValuePairList): SessionState {
        val receivedState = SessionProcessState.newBuilder()
            .setLastProcessedSequenceNum(0)
            .setUndeliveredMessages(mutableListOf())
            .build()
        val sendState = SessionProcessState.newBuilder()
            .setUndeliveredMessages(mutableListOf())
            .setLastProcessedSequenceNum(0)
            .build()
        return SessionState.newBuilder()
            .setSessionId(sessionID)
            .setSessionStartTime(createTime)
            .setLastReceivedMessageTime(createTime)
            .setCounterpartyIdentity(counterparty.toAvro())
            .setRequireClose(requireClose)
            .setReceivedEventsState(receivedState)
            .setSendEventsState(sendState)
            .setStatus(SessionStateType.CONFIRMED)
            .setHasScheduledCleanup(false)
            .setSessionProperties(sessionProperties)
            .build()
    }

    private fun generateSessionEvent(
        payload: ByteArray,
        sendSessionState: SessionState,
        initiatingIdentity: HoldingIdentity,
        initiatedIdentity: HoldingIdentity,
        init: SessionInit? = null
    ) : SessionEvent {
        val time = Instant.now()
        val sessionID = toggleSessionID(sendSessionState.sessionId)
        val sequenceNumber = sendSessionState.sendEventsState.lastProcessedSequenceNum + 1
        sendSessionState.sendEventsState.lastProcessedSequenceNum = sequenceNumber
        val data = SessionData.newBuilder()
            .setPayload(payload)
            .setSessionInit(init)
            .build()
        return SessionEvent.newBuilder()
            .setSessionId(sessionID)
            .setMessageDirection(MessageDirection.INBOUND)
            .setTimestamp(time)
            .setSequenceNum(sequenceNumber)
            .setInitiatingIdentity(initiatingIdentity.toAvro())
            .setInitiatedIdentity(initiatedIdentity.toAvro())
            .setContextSessionProperties(sendSessionState.sessionProperties)
            .setPayload(data)
            .build()
    }

    private fun calculateInitiatingAndInitiated(
        sessionMap: Map<String, SessionState>
    ): Pair<HoldingIdentity, HoldingIdentity> {
        if (sessionMap.size != 2) {
            throw IllegalArgumentException("Require two sessions to calculate initiating and initiated")
        }
        val (initiating, initiated) = sessionMap.keys.partition { !it.endsWith(INITIATED_SUFFIX) }
        if (initiating.size != 1 || initiated.size != 1) {
            throw IllegalArgumentException("Could not partition into an initiating and initiated session")
        }
        val initiatingIdentity = sessionMap[initiated.first()]!!.counterpartyIdentity
        val initiatedIdentity = sessionMap[initiating.first()]!!.counterpartyIdentity
        return Pair(initiatingIdentity.toCorda(), initiatedIdentity.toCorda())
    }

    private fun toggleSessionID(sessionID: String) : String {
        return if (sessionID.endsWith(INITIATED_SUFFIX)) {
            sessionID.substringBefore(INITIATED_SUFFIX)
        } else {
            sessionID + INITIATED_SUFFIX
        }
    }
}