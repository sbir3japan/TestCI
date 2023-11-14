package net.corda.flow.session.impl

import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.session.SessionProcessState
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.flow.session.SessionManager
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Flow.FLOW_EVENT_TOPIC
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
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
    override fun createSession(sessionID: String, config: SessionManager.SessionConfig) {
        if (stateManagerHelper.getStates(setOf(sessionID)).containsKey(sessionID)) {
            logger.info("$sessionID already exists")
            return
        } else {
            logger.info("Creating session with ID $sessionID")
        }
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
            .setContextPlatformProperties(KeyValuePairList(listOf()))
            .setContextUserProperties(KeyValuePairList(listOf()))
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
        val flowID = UUID.randomUUID().toString()
        publisher.publish(listOf(Record(
            FLOW_EVENT_TOPIC,
            flowID,
            FlowEvent(flowID, initEvent)))
        )
        logger.info("Created sessions $sessionID and $counterpartySessionID")
    }

    // TODO: deduplication of outbound session messages.
    override fun sendMessage(sessionID: String, message: ByteArray) {
        logger.info("Sending message from session $sessionID")
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

    // This version blocks
    override fun receiveMessage(sessionID: String): ByteArray {
        logger.info("Receiving message on session $sessionID")
        var message: ByteArray? = null
        val transform = { state: SessionState ->
            val undelivered = state.receivedEventsState.undeliveredMessages
            val data = undelivered.removeFirstOrNull()?.payload as? SessionData
            message = data?.payload as? ByteArray
            if (message == null) throw RetryException("Failed to retrieve message")
            state.receivedEventsState.undeliveredMessages = undelivered
            state
        }
        stateManagerHelper.updateSessionStates(mapOf(sessionID to transform))
        return message ?: throw IllegalArgumentException("Failed to get message in timeout")
    }

    override fun deleteSession(sessionID: String) {
        logger.info("Deleting session with ID $sessionID")
        val counterpartyId = toggleSessionID(sessionID)
        val states = stateManagerHelper.getStates(setOf(sessionID, counterpartyId))
        if (!states.containsKey(sessionID)) return
        val requireClose = states[sessionID]!!.requireClose
        val isInitiating = isInitiating(sessionID)

        when {
            !requireClose -> {
                // No updates to perform
            }
            isInitiating -> {
                val transform = { state: SessionState ->
                    val undelivered = state.receivedEventsState.undeliveredMessages
                    val possibleClose = undelivered.removeFirstOrNull()
                    if (possibleClose?.payload !is SessionClose) {
                        throw RetryException("No close received")
                    }
                    state
                }
                stateManagerHelper.updateSessionStates(mapOf(sessionID to transform))
            }
            else -> {
                val close = generateSessionClose(sessionID)
                val transform = { state: SessionState ->
                    val undelivered = state.receivedEventsState.undeliveredMessages
                    val newUndelivered = undelivered + close
                    state.receivedEventsState.undeliveredMessages = newUndelivered
                    state
                }
                stateManagerHelper.updateSessionStates(mapOf(counterpartyId to transform))
            }
        }
        stateManagerHelper.deleteStates(setOf(sessionID))
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
        sessionProperties: KeyValuePairList = KeyValuePairList(listOf())
    ) : SessionEvent {
        val time = Instant.now()
        val sequenceNumber = 1
        val data = SessionData.newBuilder()
            .setPayload(ByteBuffer.wrap(payload))
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

    private fun generateSessionClose(sessionID: String): SessionEvent {
        val time = Instant.now()
        val sequenceNumber = 1
        val close = SessionClose.newBuilder()
            .build()
        return SessionEvent.newBuilder()
            .setSessionId(sessionID)
            .setMessageDirection(MessageDirection.INBOUND)
            .setTimestamp(time)
            .setSequenceNum(sequenceNumber)
            .setInitiatingIdentity(dummyHoldingIdentity.toAvro())
            .setInitiatedIdentity(dummyHoldingIdentity.toAvro())
            .setContextSessionProperties(KeyValuePairList(listOf()))
            .setPayload(close)
            .build()
    }

    private fun toggleSessionID(sessionID: String) : String {
        return if (isInitiating(sessionID)) {
            sessionID + INITIATED_SUFFIX
        } else {
            sessionID.substringBefore(INITIATED_SUFFIX)
        }
    }

    private fun isInitiating(sessionID: String) : Boolean {
        return !sessionID.endsWith(INITIATED_SUFFIX)
    }

    override fun close() {
        stateManagerHelper.close()
        publisher.close()
    }
}