package net.corda.p2p.linkmanager.inbound

import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.data.p2p.AuthenticatedMessageAck
import net.corda.data.p2p.AuthenticatedMessageAndKey
import net.corda.data.p2p.DataMessagePayload
import net.corda.data.p2p.HeartbeatMessage
import net.corda.data.p2p.HeartbeatMessageAck
import net.corda.data.p2p.LinkInMessage
import net.corda.data.p2p.LinkOutMessage
import net.corda.data.p2p.MessageAck
import net.corda.data.p2p.SessionPartitions
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.InboundUnauthenticatedMessage
import net.corda.data.p2p.crypto.AuthenticatedDataMessage
import net.corda.data.p2p.crypto.AuthenticatedEncryptedDataMessage
import net.corda.data.p2p.crypto.InitiatorHandshakeMessage
import net.corda.data.p2p.crypto.InitiatorHelloMessage
import net.corda.data.p2p.crypto.ResponderHandshakeMessage
import net.corda.data.p2p.crypto.ResponderHelloMessage
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.LinkManager
import net.corda.p2p.linkmanager.common.AvroSealedClasses
import net.corda.p2p.linkmanager.common.MessageConverter
import net.corda.p2p.linkmanager.membership.NetworkMessagingValidator
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.data.p2p.markers.AppMessageMarker
import net.corda.data.p2p.markers.LinkManagerReceivedMarker
import net.corda.p2p.linkmanager.ItemWithSource
import net.corda.p2p.linkmanager.metrics.recordInboundHeartbeatMessagesMetric
import net.corda.p2p.linkmanager.metrics.recordInboundMessagesMetric
import net.corda.p2p.linkmanager.metrics.recordInboundSessionMessagesMetric
import net.corda.p2p.linkmanager.metrics.recordOutboundSessionMessagesMetric
import net.corda.p2p.linkmanager.sessions.StatefulSessionManagerImpl.Companion.LINK_MANAGER_SUBSYSTEM
import net.corda.schema.Schemas
import net.corda.tracing.traceEventProcessing
import net.corda.utilities.debug
import net.corda.utilities.flags.Features
import net.corda.utilities.time.Clock
import net.corda.virtualnode.toCorda
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
internal class InboundMessageProcessor(
    private val sessionManager: SessionManager,
    private val groupPolicyProvider: GroupPolicyProvider,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    private val inboundAssignmentListener: InboundAssignmentListener,
    private val clock: Clock,
    private val networkMessagingValidator: NetworkMessagingValidator =
        NetworkMessagingValidator(membershipGroupReaderProvider),
    private val features: Features = Features(),
) :
    EventLogProcessor<String, LinkInMessage> {

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.name)
        const val TRACE_EVENT_NAME = "P2P Link Manager Inbound Event"
    }

    private data class BusInboundMessage(
        val record: EventLogRecord<String, LinkInMessage>
    ): InboundMessage {
        override val message = record.value
    }

    override fun onNext(events: List<EventLogRecord<String, LinkInMessage>>): List<Record<*, *>> {
        return handleRequests(
            events.map { BusInboundMessage(it) }
        ).flatMap { traceable ->
            traceEventProcessing(traceable.source.record, TRACE_EVENT_NAME) { traceable.item.records }
            traceable.item.records
        }
    }
    internal fun <T: InboundMessage> handleRequests(
        messages: Collection<T>,
    ): List<ItemWithSource<T, InboundResponse>> {
        val dataMessages = mutableListOf<SessionIdAndMessage<T>>()
        val sessionMessages = mutableListOf<ItemWithSource<T, LinkInMessage>>()
        val recordsForUnauthenticatedMessage = mutableListOf<ItemWithSource<T, InboundResponse>>()

        messages.forEach { source ->
            val message = source.message
            when (val payload = message?.payload) {
                is AuthenticatedDataMessage -> {
                    payload.header.sessionId.let { sessionId ->
                        dataMessages.add(
                            SessionIdAndMessage(sessionId,
                                ItemWithSource(AvroSealedClasses.DataMessage.Authenticated(payload), source)
                            )
                        )
                    }
                }
                is AuthenticatedEncryptedDataMessage -> {
                    payload.header.sessionId.let { sessionId ->
                        dataMessages.add(
                            SessionIdAndMessage(sessionId,
                                ItemWithSource(AvroSealedClasses.DataMessage.AuthenticatedAndEncrypted(payload), source)
                            )
                        )
                    }
                }
                is ResponderHelloMessage, is ResponderHandshakeMessage, is InitiatorHandshakeMessage, is InitiatorHelloMessage -> {
                    sessionMessages.add(
                        ItemWithSource(message, source)
                    )
                }
                is InboundUnauthenticatedMessage -> {
                    logger.debug {
                        "Processing unauthenticated message ${payload.header.messageId}"
                    }
                    recordInboundMessagesMetric(payload)
                    recordsForUnauthenticatedMessage.add(
                        ItemWithSource(
                            InboundResponse(
                                listOf(
                                    Record(
                                        Schemas.P2P.P2P_IN_TOPIC,
                                        LinkManager.generateKey(),
                                        AppMessage(payload),
                                    )
                                ),
                            ),
                            source,
                        )
                    )

                }
                null -> logger.error("Received null message. The message was discarded.")
                else -> {
                    logger.error("Received unknown payload type ${payload::class.java.simpleName}. The message was discarded.")
                }
            }
        }

        return (processSessionMessages(sessionMessages) + processDataMessages(dataMessages) + recordsForUnauthenticatedMessage)
    }

    private fun <T: InboundMessage> processSessionMessages(messages: List<ItemWithSource<T, LinkInMessage>>):
            List<ItemWithSource<T, InboundResponse>> {
        recordInboundSessionMessagesMetric(messages.size)
        val responses = sessionManager.processSessionMessages(messages) { message ->
            message.item
        }
        return responses.map { (traceableMessage, response) ->
            if (response != null) {
                when (val payload = response.payload) {
                    is ResponderHelloMessage -> {
                        val partitionsAssigned = inboundAssignmentListener.getCurrentlyAssignedPartitions()
                        if (partitionsAssigned.isNotEmpty()) {
                            recordOutboundSessionMessagesMetric(response.header.sourceIdentity)
                            ItemWithSource(
                                InboundResponse(
                                    listOf(
                                        Record(Schemas.P2P.LINK_OUT_TOPIC, LinkManager.generateKey(), response),
                                        Record(
                                            Schemas.P2P.SESSION_OUT_PARTITIONS,
                                            payload.header.sessionId,
                                            SessionPartitions(partitionsAssigned.toList())
                                        )
                                    ),
                                ),
                                traceableMessage.source
                            )
                        } else {
                            logger.warn(
                                "No partitions from topic ${Schemas.P2P.LINK_IN_TOPIC} are currently assigned to " +
                                        "the inbound message processor." +
                                        " Not going to reply to session initiation for session ${payload.header.sessionId}."
                            )
                            ItemWithSource(
                                InboundResponse(
                                    emptyList(),
                                ),
                                traceableMessage.source
                            )
                        }
                    }
                    else -> {
                        recordOutboundSessionMessagesMetric(response.header.sourceIdentity)
                        ItemWithSource(
                            InboundResponse(
                                listOf(Record(Schemas.P2P.LINK_OUT_TOPIC, LinkManager.generateKey(), response)),
                            ),
                            traceableMessage.source,
                        )
                    }
                }
            } else {
                ItemWithSource(
                    InboundResponse(emptyList()),
                    traceableMessage.source,
                )
            }
        }
    }

    internal data class SessionIdAndMessage<T: InboundMessage>(
        val sessionId: String,
        val message: ItemWithSource<T, out AvroSealedClasses.DataMessage>
    )

    private fun <T: InboundMessage> processDataMessages(
        sessionIdAndMessages: List<SessionIdAndMessage<T>>
    ): List<ItemWithSource<T, InboundResponse>> {
        return sessionManager.getSessionsById(sessionIdAndMessages) { it.sessionId }.mapNotNull { (sessionIdAndMessage, sessionDirection) ->
            when (sessionDirection) {
                is SessionManager.SessionDirection.Inbound ->
                    processInboundDataMessages(sessionIdAndMessage, sessionDirection)?.let {
                        ItemWithSource(
                            it,
                            sessionIdAndMessage.message.source
                        )
                    }

                is SessionManager.SessionDirection.Outbound -> processOutboundDataMessage(sessionIdAndMessage, sessionDirection)?.let {
                    ItemWithSource(
                        InboundResponse(
                            listOf(it),
                        ),
                        sessionIdAndMessage.message.source,
                    )
                }
                is SessionManager.SessionDirection.NoSession -> {
                    logger.warn("Received message with SessionId = ${sessionIdAndMessage.sessionId} for which there is no active session." +
                            " The message was discarded."
                    )
                    null
                }
            }
        }
    }

    private fun <T: InboundMessage> processInboundDataMessages(
        sessionIdAndMessage: SessionIdAndMessage<T>,
        sessionDirection: SessionManager.SessionDirection.Inbound
    ): InboundResponse? {
        sessionManager.dataMessageReceived(
            sessionIdAndMessage.sessionId,
            sessionDirection.counterparties.counterpartyId,
            sessionDirection.counterparties.ourId
        )
        return if (isCommunicationAllowed(sessionDirection.counterparties)) {
            processLinkManagerPayload(
                sessionDirection.counterparties,
                sessionDirection.session,
                sessionIdAndMessage.sessionId,
                sessionIdAndMessage.message.item
            )
        } else {
            null
        }
    }

    private fun <T: InboundMessage> processOutboundDataMessage(
        sessionIdAndMessage: SessionIdAndMessage<T>,
        sessionDirection: SessionManager.SessionDirection.Outbound
    ): Record<*, *>?  {
        return if (isCommunicationAllowed(sessionDirection.counterparties)) {
            MessageConverter.extractPayload(
                sessionDirection.session,
                sessionIdAndMessage.sessionId,
                sessionIdAndMessage.message.item,
                MessageAck::fromByteBuffer
            )?.let {
                when (val ack = it.ack) {
                    is AuthenticatedMessageAck -> {
                        logger.debug { "Processing ack for message ${ack.messageId} from session $sessionIdAndMessage." }
                        sessionManager.messageAcknowledged(sessionIdAndMessage.sessionId)
                        val record = makeMarkerForAckMessage(ack)
                        record
                    }
                    is HeartbeatMessageAck -> {
                        logger.debug { "Processing heartbeat ack from session $sessionIdAndMessage." }
                        sessionManager.messageAcknowledged(sessionIdAndMessage.sessionId)
                        null
                    }
                    else -> {
                        logger.warn("Received an inbound message with unexpected type for SessionId = $sessionIdAndMessage.")
                        null
                    }
                }
            }
        } else {
            null
        }
    }


    private fun checkIdentityBeforeProcessing(
        counterparties: SessionManager.Counterparties,
        innerMessage: AuthenticatedMessageAndKey,
        session: Session,
    ): InboundResponse? {
        val sessionSource = counterparties.counterpartyId
        val sessionDestination = counterparties.ourId
        val messageDestination = innerMessage.message.header.destination
        val messageSource = innerMessage.message.header.source
        return if (sessionSource == messageSource.toCorda() && sessionDestination == messageDestination.toCorda()) {
            logger.debug {
                "Processing message ${innerMessage.message.header.messageId} " +
                    "of type ${innerMessage.message.javaClass} from session ${session.sessionId}"
            }
            makeAckMessageForFlowMessage(innerMessage.message, session)?.plus(
                Record(Schemas.P2P.P2P_IN_TOPIC, innerMessage.key, AppMessage(innerMessage.message))
            )?.also {
                sessionManager.inboundSessionEstablished(session.sessionId)
            }
        } else if (sessionSource != messageSource.toCorda()) {
            logger.warn(
                "The identity in the message's source header ($messageSource)" +
                    " does not match the session's source identity ($sessionSource)," +
                    " which indicates a spoofing attempt! The message was discarded."
            )
            null
        } else {
            logger.warn(
                "The identity in the message's destination header ($messageDestination)" +
                    " does not match the session's destination identity ($sessionDestination)," +
                    " which indicates a spoofing attempt! The message was discarded"
            )
            null
        }
    }

    private fun processLinkManagerPayload(
        counterparties: SessionManager.Counterparties,
        session: Session,
        sessionId: String,
        message: AvroSealedClasses.DataMessage
    ): InboundResponse? {
        return MessageConverter.extractPayload(session, sessionId, message, DataMessagePayload::fromByteBuffer)?.let {
            when (val innerMessage = it.message) {
                is HeartbeatMessage -> {
                    logger.debug { "Processing heartbeat message from session $sessionId" }
                    recordInboundHeartbeatMessagesMetric(counterparties.counterpartyId)
                    makeAckMessageForHeartbeatMessage(counterparties, session)?.let { ack ->
                        InboundResponse(listOf(ack))
                    }
                }
                is AuthenticatedMessageAndKey -> {
                    val authenticatedMessage = innerMessage.message
                    recordInboundMessagesMetric(authenticatedMessage)
                    if (authenticatedMessage.header.subsystem == LINK_MANAGER_SUBSYSTEM) {
                        logger.info("Received message indicating a session was lost by the counterparty. The " +
                                "corresponding outbound session will be deleted.")
                        sessionManager.deleteOutboundSession(counterparties.reverse(), authenticatedMessage)
                        null
                    } else {
                        checkIdentityBeforeProcessing(
                            counterparties, innerMessage, session
                        )
                    }
                }
                else -> {
                    logger.warn("Unknown incoming message type: ${innerMessage.javaClass}. The message was discarded.")
                    null
                }
            }
        }
    }

    private fun makeAckMessageForHeartbeatMessage(
        counterparties: SessionManager.Counterparties,
        session: Session
    ): Record<String, LinkOutMessage>? {
        val ackDest = counterparties.counterpartyId
        val ackSource = counterparties.ourId
        val ack = MessageConverter.linkOutMessageFromAck(
            MessageAck(HeartbeatMessageAck()),
            ackSource,
            ackDest,
            session,
            groupPolicyProvider,
            membershipGroupReaderProvider,
        ) ?: return null
        return Record(
            Schemas.P2P.LINK_OUT_TOPIC,
            LinkManager.generateKey(),
            ack
        )
    }

    private fun makeAckMessageForFlowMessage(
        message: AuthenticatedMessage,
        session: Session
    ): InboundResponse? {
        // We route the ACK back to the original source
        val ackDest = message.header.source.toCorda()
        val ackSource = message.header.destination.toCorda()
        val ackMessage = MessageAck(AuthenticatedMessageAck(message.header.messageId))
        return if (features.enableP2PGatewayToLinkManagerOverHttp) {
            InboundResponse(
                emptyList(),
                MessageConverter.createLinkManagerResponse(ackMessage, session),
            )
        } else {
            val ack = MessageConverter.linkOutMessageFromAck(
                MessageAck(AuthenticatedMessageAck(message.header.messageId)),
                ackSource,
                ackDest,
                session,
                groupPolicyProvider,
                membershipGroupReaderProvider
            ) ?: return null
            val record = Record(
                Schemas.P2P.LINK_OUT_TOPIC,
                LinkManager.generateKey(),
                ack
            )
            InboundResponse(listOf(record))
        }
    }

    private fun makeMarkerForAckMessage(message: AuthenticatedMessageAck): Record<String, AppMessageMarker> {
        return Record(
            Schemas.P2P.P2P_OUT_MARKERS,
            message.messageId,
            AppMessageMarker(LinkManagerReceivedMarker(), clock.instant().toEpochMilli())
        )
    }

    private fun isCommunicationAllowed(
        counterparties: SessionManager.Counterparties,
    ): Boolean = networkMessagingValidator.isValidInbound(
        counterparties.counterpartyId,
        counterparties.ourId,
    )


    override val keyClass = String::class.java
    override val valueClass = LinkInMessage::class.java
}
