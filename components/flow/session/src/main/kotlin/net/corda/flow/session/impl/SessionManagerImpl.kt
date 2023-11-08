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
        val initiatorState = createNewState(sessionID, createdTime, config.counterparty, config.requireClose)
        val initiatedState = createNewState(counterpartySessionID, createdTime, config.party, config.requireClose)
        initiatorState.sendEventsState.lastProcessedSequenceNum = 1
        // Publish a record to start the counterparty flow.
        val initPayload = SessionInit.newBuilder()
            .setCpiId(config.cpiId)
            .setContextPlatformProperties(null)
            .setContextUserProperties(null)
            .setFlowId(null)
            .build()
        val dummyData = SessionData.newBuilder()
            .setPayload(byteArrayOf())
            .setSessionInit(initPayload)
            .build()
        val initEvent = SessionEvent.newBuilder()
            .setSessionId(counterpartySessionID)
            .setMessageDirection(MessageDirection.INBOUND)
            .setTimestamp(createdTime)
            .setSequenceNum(1)
            .setInitiatingIdentity(config.party.toAvro())
            .setInitiatedIdentity(config.counterparty.toAvro())
            .setContextSessionProperties(config.contextSessionProperties)
            .setPayload(dummyData)
            .build()
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

    private fun createNewState(
        sessionID: String,
        createTime: Instant,
        counterparty: HoldingIdentity,
        requireClose: Boolean): SessionState {
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
            .setSessionProperties(KeyValuePairList())
            .build()
    }

    override fun sendMessage(sessionID: String, message: ByteArray) {
        TODO("Not yet implemented")
    }

    override fun receiveMessage(sessionID: String): ByteArray {
        TODO("Not yet implemented")
    }

    override fun deleteSession(sessionID: String) {
        TODO("Not yet implemented")
    }
}