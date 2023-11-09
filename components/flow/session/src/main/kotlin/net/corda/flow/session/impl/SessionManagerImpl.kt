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
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.UUID

// For this prototype, assume all virtual nodes are local.
class SessionManagerImpl(
    private val stateManagerHelper: StateManagerHelper,
    private val publisher: Publisher
) : SessionManager {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val INITIATED_SUFFIX = "-INITIATED"
        private val dummyHoldingIdentity = HoldingIdentity(MemberX500Name.parse("O=Alice, L=London, C=GB"), "foo")
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
        val initEvent = generateSessionEvent(
            byteArrayOf(),
            toggleSessionID(sessionID),
            initPayload,
            config.party,
            config.counterparty,
            config.contextSessionProperties
        )
        initiatedState.receivedEventsState.lastProcessedSequenceNum = 1
        // Set up sessions.
        stateManagerHelper.createSessionStates(setOf(initiatorState, initiatedState))
        publisher.publish(listOf(Record(FLOW_EVENT_TOPIC, counterpartySessionID, FlowEvent(counterpartySessionID, initEvent))))
        return sessionID
    }

    // TODO: deduplication of outbound session messages.
    override fun sendMessage(sessionID: String, message: ByteArray) {
        val receivingID = toggleSessionID(sessionID)
        val sessionEvent = generateSessionEvent(
            message,
            sessionID
        )
        val sendTransform = { state: SessionState ->
            state.sendEventsState.lastProcessedSequenceNum += 1
            state
        }
        val receiveTransform = { state: SessionState ->
            val undelivered = state.receivedEventsState.undeliveredMessages
            state.receivedEventsState.undeliveredMessages = undelivered + sessionEvent
            state
        }
        stateManagerHelper.updateSessionStates(mapOf(sessionID to sendTransform, receivingID to receiveTransform))
    }

    override fun receiveMessage(sessionID: String): ByteArray {
        var message: ByteArray? = null
        val time = Instant.now()
        val transform = { state: SessionState ->
            val undelivered = state.receivedEventsState.undeliveredMessages
            val data = undelivered.removeFirstOrNull()?.payload as? SessionData
            message = data?.payload as? ByteArray
            state.receivedEventsState.undeliveredMessages = undelivered
            state
        }
        do {
            stateManagerHelper.updateSessionStates(mapOf(sessionID to transform))
        } while (message == null && time + Duration.ofMillis(5000L) < Instant.now())
        return message ?: throw IllegalArgumentException("Failed to get message in 5s")
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
        sessionID: String,
        init: SessionInit? = null,
        initiatingIdentity: HoldingIdentity = dummyHoldingIdentity,
        initatedIdentity: HoldingIdentity = dummyHoldingIdentity,
        sessionProperties: KeyValuePairList = KeyValuePairList()
    ) : SessionEvent {
        val time = Instant.now()
        val sequenceNumber = 1
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
            .setInitiatedIdentity(initatedIdentity.toAvro())
            .setContextSessionProperties(sessionProperties)
            .setPayload(data)
            .build()
    }

    private fun toggleSessionID(sessionID: String) : String {
        return if (sessionID.endsWith(INITIATED_SUFFIX)) {
            sessionID.substringBefore(INITIATED_SUFFIX)
        } else {
            sessionID + INITIATED_SUFFIX
        }
    }
}