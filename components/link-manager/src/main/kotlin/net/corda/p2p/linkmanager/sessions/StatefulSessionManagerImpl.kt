package net.corda.p2p.linkmanager.sessions

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.crypto.client.SessionEncryptionOpsClient
import net.corda.crypto.core.SecureHashImpl
import net.corda.data.p2p.AuthenticatedMessageAndKey
import net.corda.data.p2p.LinkInMessage
import net.corda.data.p2p.LinkOutMessage
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.Operation
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.p2p.crypto.protocol.api.AuthenticatedEncryptionSession
import net.corda.p2p.crypto.protocol.api.AuthenticatedSession
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.membership.lookup
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.alreadySessionWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.noSessionWarning
import net.corda.p2p.linkmanager.sessions.metadata.CommonMetadata
import net.corda.p2p.linkmanager.sessions.metadata.CommonMetadata.Companion.toCommonMetadata
import net.corda.p2p.linkmanager.sessions.metadata.InboundSessionMetadata
import net.corda.p2p.linkmanager.sessions.metadata.InboundSessionMetadata.Companion.toInbound
import net.corda.p2p.linkmanager.sessions.metadata.InboundSessionStatus
import net.corda.p2p.linkmanager.sessions.metadata.OutboundSessionMetadata
import net.corda.p2p.linkmanager.sessions.metadata.OutboundSessionMetadata.Companion.toOutbound
import net.corda.p2p.linkmanager.sessions.metadata.OutboundSessionStatus
import net.corda.p2p.linkmanager.state.SessionState
import net.corda.utilities.time.Clock
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.time.Duration
import net.corda.data.p2p.crypto.InitiatorHandshakeMessage as AvroInitiatorHandshakeMessage
import net.corda.data.p2p.crypto.InitiatorHelloMessage as AvroInitiatorHelloMessage
import net.corda.data.p2p.crypto.ResponderHandshakeMessage as AvroResponderHandshakeMessage
import net.corda.data.p2p.crypto.ResponderHelloMessage as AvroResponderHelloMessage
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionState.CannotEstablishSession as CannotEstablishSession
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionState.NewSessionsNeeded as NewSessionsNeeded
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionState.SessionAlreadyPending as SessionAlreadyPending
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionState.SessionEstablished as SessionEstablished

@Suppress("TooManyFunctions", "LongParameterList", "LargeClass")
internal class StatefulSessionManagerImpl(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val stateManager: StateManager,
    private val sessionManagerImpl: SessionManagerImpl,
    private val stateConvertor: StateConvertor,
    private val clock: Clock,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
) : SessionManager {
    private companion object {
        const val CACHE_SIZE = 10_000L
        val SESSION_VALIDITY_PERIOD: Duration = Duration.ofDays(7)
        val logger: Logger = LoggerFactory.getLogger(StatefulSessionManagerImpl::class.java)
    }

    override fun <T> processOutboundMessages(
        wrappedMessages: Collection<T>,
        getMessage: (T) -> AuthenticatedMessageAndKey,
    ): Collection<Pair<T, SessionManager.SessionState>> {
        val messages =
            wrappedMessages.map {
                OutboundMessageContext(it, getMessage(it))
            }
        val keysToMessages =
            messages.groupBy {
                val messageHeader = it.message.message.header
                val serial =
                    getCounterpartySerial(
                        messageHeader.source.toCorda(),
                        messageHeader.destination.toCorda(),
                        messageHeader.statusFilter,
                    )
                if (serial == null) {
                    logger.warn(
                        "Cannot establish session for message ${messageHeader.messageId}: Failed to look up counterparty.",
                    )
                    null
                } else {
                    calculateOutboundSessionKey(
                        messageHeader.source.toCorda(),
                        messageHeader.destination.toCorda(),
                        serial,
                    )
                }
            }
        val keysToQuery = keysToMessages.keys.filterNotNull()
        val sessionStates =
            if (keysToQuery.isNotEmpty()) {
                stateManager.get(keysToQuery).let { states ->
                    keysToMessages.map { (id, items) ->
                        OutboundMessageState(
                            id,
                            states[id],
                            items,
                        )
                    }
                }
            } else {
                val messagesWithoutKey = keysToMessages[null] ?: return emptyList()
                listOf(
                    OutboundMessageState(
                        null,
                        null,
                        messagesWithoutKey,
                    ),
                )
            }
        val resultStates =
            sessionStates.flatMap { state ->
                processOutboundMessagesState(state)
            }

        return processStateUpdates(resultStates)
    }

    private fun <T> processOutboundMessagesState(
        state: OutboundMessageState<T>,
    ): Collection<OutboundMessageResults<T>> {
        if (state.key == null) {
            return state.toResults(
                CannotEstablishSession,
            )
        }
        val counterparties = state.messages.first().sessionCounterparties()
            ?: return state.toResults(
                CannotEstablishSession,
            )
        val metadata = state.state?.metadata?.toOutbound()
        return if (metadata?.lastSendExpired(clock) == false) {
            when (metadata.status) {
                OutboundSessionStatus.SentInitiatorHello, OutboundSessionStatus.SentInitiatorHandshake -> {
                    state.toResults(
                        SessionAlreadyPending(counterparties),
                    )
                }
                OutboundSessionStatus.SessionReady -> {
                    state.state.retrieveEstablishedSession(counterparties)?.let { establishedState ->
                        state.toResults(establishedState)
                    } ?: state.toResults(CannotEstablishSession)
                }
            }
        } else {
            when (metadata?.status) {
                null -> {
                    newSessionNeeded(
                        counterparties,
                        state.first.message.message.header.statusFilter,
                    )?.let { (needed, newState) ->
                        state.toResultsFirstAndOther(
                            update = newState,
                            firstState = needed,
                            otherStates = SessionAlreadyPending(counterparties),
                        )
                    } ?: state.toResults(
                        CannotEstablishSession,
                    )
                }

                OutboundSessionStatus.SentInitiatorHello, OutboundSessionStatus.SentInitiatorHandshake -> {
                    state.state.replaySessionMessage()?.let { (needed, newState) ->
                        state.toResultsFirstAndOther(
                            update = newState,
                            firstState = needed,
                            otherStates = SessionAlreadyPending(counterparties),
                        )
                    } ?: state.toResults(
                        CannotEstablishSession,
                    )
                }

                OutboundSessionStatus.SessionReady -> {
                    state.state.retrieveEstablishedSession(counterparties)?.let { established ->
                        state.toResults(
                            established,
                        )
                    } ?: state.toResults(
                        CannotEstablishSession,
                    )
                }
            }
        }
    }

    override fun <T> getSessionsById(
        uuids: Collection<T>,
        getSessionId: (T) -> String,
    ): Collection<Pair<T, SessionManager.SessionDirection>> {
        if (uuids.isEmpty()) {
            return emptyList()
        }
        val traceable = uuids.associateBy { getSessionId(it) }
        val sessionFromInboundCache = cachedInboundSessions.getAllPresent(traceable.keys)
        val allCached =
            sessionFromInboundCache +
                cachedOutboundSessions.getAllPresent((traceable - sessionFromInboundCache.keys).keys)
        val sessionIdsNotInCache = traceable - allCached.keys
        val inboundSessionsFromStateManager: List<Pair<T, SessionManager.SessionDirection>> =
            if (sessionIdsNotInCache.isEmpty()) {
                emptyList()
            } else {
                stateManager.get(sessionIdsNotInCache.keys).entries.mapNotNull { (sessionId, state) ->
                    val session =
                        stateConvertor.toCordaSessionState(
                            state,
                            sessionManagerImpl.revocationCheckerClient::checkRevocation,
                        ).sessionData as? Session
                    session?.let {
                        sessionIdsNotInCache[sessionId]?.let {
                            val inboundSession =
                                SessionManager.SessionDirection.Inbound(
                                    state.toCounterparties(),
                                    session,
                                )
                            cachedInboundSessions.put(sessionId, inboundSession)
                            it to inboundSession
                        }
                    }
                }
            }
        val sessionsNotInInboundStateManager =
            (sessionIdsNotInCache.keys - inboundSessionsFromStateManager.map { getSessionId(it.first) }.toSet()).map {
                getSessionIdFilter(it)
            }
        val outboundSessionsFromStateManager: List<Pair<T, SessionManager.SessionDirection>> =
            if (sessionsNotInInboundStateManager.isEmpty()) {
                emptyList()
            } else {
                stateManager.findByMetadataMatchingAny(sessionsNotInInboundStateManager).entries.mapNotNull { (_, state) ->
                    val session =
                        stateConvertor.toCordaSessionState(
                            state,
                            sessionManagerImpl.revocationCheckerClient::checkRevocation,
                        ).sessionData as? Session
                    val sessionId = state.metadata.toOutbound().sessionId
                    session?.let {
                        sessionIdsNotInCache[sessionId]?.let {
                            val outboundSession =
                                SessionManager.SessionDirection.Outbound(
                                    state.toCounterparties(),
                                    session,
                                )
                            cachedOutboundSessions.put(sessionId, outboundSession)
                            it to outboundSession
                        }
                    }
                }
            }

        return allCached.mapNotNull { (sessionId, sessionDirection) ->
            traceable[sessionId]?.let { it to sessionDirection }
        } + inboundSessionsFromStateManager + outboundSessionsFromStateManager
    }

    override fun <T> processSessionMessages(
        wrappedMessages: Collection<T>,
        getMessage: (T) -> LinkInMessage,
    ): Collection<Pair<T, LinkOutMessage?>> {
        val messages = wrappedMessages.map { it to getMessage(it) }
        val results = processInboundSessionMessages(messages) + processOutboundSessionMessages(messages)

        val failedUpdate =
            stateManager.update(results.mapNotNull { it.result?.stateUpdate })
                .keys.onEach {
                    logger.info("Failed to update the state of session $it")
                }

        return results.mapNotNull { result ->
            if (failedUpdate.contains(result.result?.stateUpdate?.key)) {
                null
            } else {
                result
            }
        }.onEach { result ->
            when (result.result?.message?.payload) {
                is AvroResponderHelloMessage, is AvroResponderHandshakeMessage -> {
                    result.result.sessionToCache?.let { sessionToCache ->
                        val session = SessionManager.SessionDirection.Inbound(
                            result.result.stateUpdate.toCounterparties().reverse(),
                            sessionToCache,
                        )
                        cachedInboundSessions.put(
                            sessionToCache.sessionId,
                            session,
                        )
                    }
                }
                is AvroInitiatorHelloMessage, is AvroInitiatorHandshakeMessage -> {
                    result.result.sessionToCache?.let { sessionToCache ->
                        cachedOutboundSessions.put(
                            sessionToCache.sessionId,
                            SessionManager.SessionDirection.Inbound(
                                result.result.stateUpdate.toCounterparties(),
                                sessionToCache,
                            ),
                        )
                    }
                }
            }
        }.map { result ->
            result.traceable to result.result?.message
        }
    }

    override fun messageAcknowledged(sessionId: String) {
        // To be implemented in CORE-18730
        return
    }

    override fun inboundSessionEstablished(sessionId: String) {
        // Not needed by the Stateful Session Manager
        return
    }

    override fun dataMessageReceived(
        sessionId: String,
        source: HoldingIdentity,
        destination: HoldingIdentity,
    ) {
        // Not needed by the Stateful Session Manager
        return
    }

    override fun dataMessageSent(session: Session) {
        // Not needed by the Stateful Session Manager
        return
    }

    private data class InboundSessionMessageContext<T>(
        val sessionId: String,
        val inboundSessionMessage: InboundSessionMessage,
        val trace: T,
    )

    private data class OutboundSessionMessageContext<T>(
        val sessionId: String,
        val outboundSessionMessage: OutboundSessionMessage,
        val trace: T,
    )

    private data class OutboundMessageContext<T>(
        val trace: T,
        val message: AuthenticatedMessageAndKey,
    )

    private data class OutboundMessageState<T>(
        val key: String?,
        val state: State?,
        val messages: Collection<OutboundMessageContext<T>>,
    ) {
        val first by lazy {
            messages.first()
        }
        val others by lazy {
            messages.drop(1)
        }

        fun toResults(
            sessionState: SessionManager.SessionState,
        ): Collection<OutboundMessageResults<T>> {
            return listOf(
                OutboundMessageResults(
                    key = this.key,
                    messages = this.messages,
                    update = null,
                    sessionState = sessionState,
                ),
            )
        }
        fun toResultsFirstAndOther(
            firstState: SessionManager.SessionState,
            otherStates: SessionManager.SessionState,
            update: State,
        ): Collection<OutboundMessageResults<T>> {
            return listOf(
                OutboundMessageResults(
                    key = this.key,
                    messages = listOf(first),
                    update = update,
                    sessionState = firstState,
                ),
                OutboundMessageResults(
                    key = this.key,
                    messages = others,
                    update = null,
                    sessionState = otherStates,
                ),
            )
        }
    }

    private data class OutboundMessageResults<T>(
        val key: String?,
        val messages: Collection<OutboundMessageContext<T>>,
        val update: State?,
        val sessionState: SessionManager.SessionState,
    )

    private fun <T> OutboundMessageContext<T>.sessionCounterparties() =
        sessionManagerImpl.getSessionCounterpartiesFromMessage(message.message)

    private fun calculateOutboundSessionKey(
        source: HoldingIdentity,
        destination: HoldingIdentity,
        serial: Long,
    ) = SessionCounterpartiesKey(source, destination, serial).hash.toHexString()

    private fun getCounterpartySerial(
        source: HoldingIdentity,
        destination: HoldingIdentity,
        status: MembershipStatusFilter,
    ): Long? = membershipGroupReaderProvider.lookup(source, destination, status)?.serial

    private data class SessionCounterpartiesKey(
        override val ourId: HoldingIdentity,
        override val counterpartyId: HoldingIdentity,
        val serial: Long,
    ) : SessionManager.BaseCounterparties {
        val hash: SecureHash by lazy(LazyThreadSafetyMode.PUBLICATION) {
            val s = (ourId.x500Name.toString() + counterpartyId.x500Name.toString() + serial.toString())
            val digest: MessageDigest = MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name)
            val hash: ByteArray = digest.digest(s.toByteArray())
            SecureHashImpl(DigestAlgorithmName.SHA2_256.name, hash)
        }
    }

    private sealed interface InboundSessionMessage {
        data class InitiatorHelloMessage(
            val initiatorHelloMessage: AvroInitiatorHelloMessage,
        ) : InboundSessionMessage

        data class InitiatorHandshakeMessage(
            val initiatorHandshakeMessage: AvroInitiatorHandshakeMessage,
        ) : InboundSessionMessage
    }

    private sealed interface OutboundSessionMessage {
        data class ResponderHelloMessage(
            val responderHelloMessage: AvroResponderHelloMessage,
        ) : OutboundSessionMessage

        data class ResponderHandshakeMessage(
            val responderHandshakeMessage: AvroResponderHandshakeMessage,
        ) : OutboundSessionMessage
    }

    private data class TraceableResult<T>(
        val traceable: T,
        val result: Result?,
    )

    private data class Result(
        val message: LinkOutMessage?,
        val stateUpdate: State,
        val sessionToCache: Session?,
    )

    private val cachedInboundSessions: Cache<String, SessionManager.SessionDirection> =
        CacheFactoryImpl().build(
            "P2P-inbound-sessions-cache",
            Caffeine.newBuilder()
                .maximumSize(CACHE_SIZE),
        )

    private val cachedOutboundSessions: Cache<String, SessionManager.SessionDirection> =
        CacheFactoryImpl().build(
            "P2P-outbound-sessions-cache",
            Caffeine.newBuilder()
                .maximumSize(CACHE_SIZE),
        )

    private fun State.toCounterparties(): SessionManager.Counterparties {
        val common = this.metadata.toCommonMetadata()
        return SessionManager.Counterparties(
            ourId = common.destination,
            counterpartyId = common.source,
        )
    }

    private fun SessionManager.Counterparties.reverse(): SessionManager.Counterparties {
        return SessionManager.Counterparties(
            ourId = this.counterpartyId,
            counterpartyId = this.ourId,
        )
    }

    private fun State.retrieveEstablishedSession(
        counterParties: SessionManager.SessionCounterparties,
    ): SessionEstablished? {
        val sessionData =
            stateConvertor.toCordaSessionState(
                this,
                sessionManagerImpl.revocationCheckerClient::checkRevocation,
            ).sessionData
        return when (sessionData) {
            is AuthenticatedSession, is AuthenticatedEncryptionSession ->
                SessionEstablished(sessionData as Session, counterParties)

            else -> null
        }
    }

    private fun newSessionNeeded(
        counterParties: SessionManager.SessionCounterparties,
        filter: MembershipStatusFilter,
    ): Pair<NewSessionsNeeded, State>? {
        val initMessage = sessionManagerImpl.genSessionInitMessages(counterParties, 1).firstOrNull() ?: return null
        val message =
            sessionManagerImpl.linkOutMessagesFromSessionInitMessages(
                counterParties,
                listOf(initMessage),
                filter,
            )?.firstOrNull() ?: return null

        val timestamp = clock.instant()
        val newMetadata =
            OutboundSessionMetadata(
                sessionId = initMessage.first.sessionId,
                commonData = CommonMetadata(
                    source = counterParties.ourId,
                    destination = counterParties.counterpartyId,
                    lastSendTimestamp = timestamp,
                    expiry = timestamp + SESSION_VALIDITY_PERIOD,
                ),
                status = OutboundSessionStatus.SentInitiatorHello,
                serial = counterParties.serial,
                membershipStatus = counterParties.status,
                communicationWithMgm = counterParties.communicationWithMgm,
            )
        val newState =
            State(
                calculateOutboundSessionKey(
                    counterParties.ourId,
                    counterParties.counterpartyId,
                    counterParties.serial,
                ),
                stateConvertor.toStateByteArray(SessionState(message.second, initMessage.first)),
                version = 0,
                metadata = newMetadata.toMetadata(),
            )
        return NewSessionsNeeded(listOf(message), counterParties) to newState
    }

    private fun State.replaySessionMessage(): Pair<NewSessionsNeeded, State>? {
        val sessionMessage =
            stateConvertor.toCordaSessionState(
                this,
                sessionManagerImpl.revocationCheckerClient::checkRevocation,
            ).message ?: return null
        val outboundMetadata = metadata.toOutbound()
        val updatedMetadata = outboundMetadata.copy(
            commonData = outboundMetadata.commonData.copy(
                lastSendTimestamp = clock.instant(),
            ),
        )
        val updatedState =
            State(
                key,
                value,
                version = version,
                metadata = updatedMetadata.toMetadata(),
            )
        return NewSessionsNeeded(
            listOf(updatedMetadata.sessionId to sessionMessage),
            updatedState.getSessionCounterparties(),
        ) to updatedState
    }
    private fun <T> processStateUpdates(
        resultStates: Collection<OutboundMessageResults<T>>,
    ): Collection<Pair<T, SessionManager.SessionState>> {
        val updates = resultStates.mapNotNull { it.update }
        val failedUpdates = if (updates.isNotEmpty()) {
            stateManager.update(updates).also {
                if (it.isNotEmpty()) {
                    logger.warn("Failed to update the following session states: ${it.keys}.")
                }
            }
        } else {
            emptyMap()
        }

        return resultStates.flatMap { resultState ->
            val key = resultState.key
            if (failedUpdates.containsKey(key)) {
                val savedState = failedUpdates[key]
                val savedMetadata = savedState?.metadata?.toOutbound()
                val savedStatus = savedMetadata?.status
                val newState = when (savedStatus) {
                    OutboundSessionStatus.SentInitiatorHello, OutboundSessionStatus.SentInitiatorHandshake ->
                        resultState.messages.first().sessionCounterparties()?.let {
                            SessionAlreadyPending(it)
                        } ?: CannotEstablishSession
                    OutboundSessionStatus.SessionReady -> resultState.messages.first().sessionCounterparties()?.let {
                        savedState.retrieveEstablishedSession(it)
                    } ?: CannotEstablishSession
                    null -> CannotEstablishSession
                }
                resultState.messages.map { it.trace to newState }
            } else {
                resultState.messages.map { it.trace to resultState.sessionState }
            }
        }
    }
    private fun <T> processInboundSessionMessages(messages: List<Pair<T, LinkInMessage?>>): Collection<TraceableResult<T>> {
        val messageContexts =
            messages.mapNotNull {
                it.second?.payload?.getSessionIdIfInboundSessionMessage(it.first)
            }
        if (messageContexts.isEmpty()) {
            return emptyList()
        }
        val states = stateManager.get(messageContexts.map { it.sessionId })
        return messageContexts.map {
            val state = states[it.sessionId]
            val result =
                when (it.inboundSessionMessage) {
                    is InboundSessionMessage.InitiatorHelloMessage -> {
                        processInitiatorHello(state, it.inboundSessionMessage)?.let { (message, stateUpdate) ->
                            Result(message, stateUpdate, null)
                        }
                    }
                    is InboundSessionMessage.InitiatorHandshakeMessage -> {
                        processInitiatorHandshake(state, it.inboundSessionMessage)?.let { (message, stateUpdate, session) ->
                            Result(message, stateUpdate, session)
                        }
                    }
                }
            TraceableResult(it.trace, result)
        }
    }

    private fun <T> processOutboundSessionMessages(messages: List<Pair<T, LinkInMessage?>>): Collection<TraceableResult<T>> {
        val messageContexts =
            messages.mapNotNull {
                it.second?.payload?.getSessionIdIfOutboundSessionMessage(it.first)
            }
        if (messageContexts.isEmpty()) {
            return emptyList()
        }
        val states =
            stateManager
                .findByMetadataMatchingAny(messageContexts.map { getSessionIdFilter(it.sessionId) })
                .values.associateBy { state ->
                    state.metadata.toOutbound().sessionId
                }
        return messageContexts.map {
            val state = states[it.sessionId]
            val result =
                when (it.outboundSessionMessage) {
                    is OutboundSessionMessage.ResponderHelloMessage -> {
                        processResponderHello(state, it.outboundSessionMessage)?.let { (message, stateUpdate) ->
                            Result(message, stateUpdate, null)
                        }
                    }
                    is OutboundSessionMessage.ResponderHandshakeMessage -> {
                        processResponderHandshake(state, it.outboundSessionMessage)?.let { (message, stateUpdate, session) ->
                            Result(message, stateUpdate, session)
                        }
                    }
                }
            TraceableResult(it.trace, result)
        }
    }

    private fun getSessionIdFilter(sessionId: String): MetadataFilter = MetadataFilter("sessionId", Operation.Equals, sessionId)

    /**
     * TODO Refactor SessionManagerImpl to move logic needed here i.e. create an ResponderHello from an InitiatorHello
     * into a new component. This component should not store the AuthenticationProtocol in an in memory map or replay session
     * messages.
     */
    private fun processInitiatorHello(
        state: State?,
        message: InboundSessionMessage.InitiatorHelloMessage,
    ): Pair<LinkOutMessage?, State>? {
        val metadata = state?.metadata?.toInbound()
        return when (metadata?.status) {
            null -> {
                sessionManagerImpl.processInitiatorHello(message.initiatorHelloMessage)?.let {
                        (responseMessage, authenticationProtocol) ->
                    val timestamp = clock.instant()
                    val newMetadata =
                        InboundSessionMetadata(
                            CommonMetadata(
                                destination = responseMessage.header.destinationIdentity.toCorda(),
                                source = responseMessage.header.sourceIdentity.toCorda(),
                                lastSendTimestamp = timestamp,
                                expiry = timestamp + SESSION_VALIDITY_PERIOD,
                            ),
                            status = InboundSessionStatus.SentResponderHello,
                        )
                    val newState =
                        State(
                            message.initiatorHelloMessage.header.sessionId,
                            stateConvertor.toStateByteArray(SessionState(responseMessage, authenticationProtocol)),
                            version = 0,
                            metadata = newMetadata.toMetadata(),
                        )
                    responseMessage to newState
                }
            }
            InboundSessionStatus.SentResponderHello -> {
                if (metadata.lastSendExpired(clock)) {
                    val timestamp = clock.instant()
                    val updatedMetadata = metadata.copy(
                        commonData = metadata.commonData.copy(
                            lastSendTimestamp = timestamp,
                        ),
                    )
                    val responderHelloToResend =
                        stateConvertor.toCordaSessionState(
                            state,
                            sessionManagerImpl.revocationCheckerClient::checkRevocation,
                        ).message
                    val newState =
                        State(
                            key = state.key,
                            value = state.value,
                            version = state.version,
                            metadata = updatedMetadata.toMetadata(),
                        )
                    responderHelloToResend to newState
                } else {
                    null
                }
            }
            InboundSessionStatus.SentResponderHandshake -> {
                null
            }
        }
    }

    private fun processResponderHello(
        state: State?,
        message: OutboundSessionMessage.ResponderHelloMessage,
    ): Pair<LinkOutMessage?, State>? {
        val metadata = state?.metadata?.toOutbound()
        return when (metadata?.status) {
            OutboundSessionStatus.SentInitiatorHello -> {
                val sessionState =
                    stateConvertor.toCordaSessionState(
                        state,
                        sessionManagerImpl.revocationCheckerClient::checkRevocation,
                    ).sessionData as AuthenticationProtocolInitiator
                val counterparties = state.getSessionCounterparties()

                sessionManagerImpl.processResponderHello(
                    counterparties,
                    sessionState,
                    message.responderHelloMessage,
                )?.let { (responseMessage, authenticationProtocol) ->
                    val timestamp = clock.instant()
                    val updatedMetadata =
                        metadata.copy(
                            commonData = metadata.commonData.copy(
                                lastSendTimestamp = timestamp,
                                expiry = timestamp + SESSION_VALIDITY_PERIOD,
                            ),
                            sessionId = message.responderHelloMessage.header.sessionId,
                            status = OutboundSessionStatus.SentInitiatorHandshake,
                        )
                    val newState =
                        State(
                            calculateOutboundSessionKey(
                                counterparties.ourId,
                                counterparties.counterpartyId,
                                counterparties.serial,
                            ),
                            stateConvertor.toStateByteArray(SessionState(responseMessage, authenticationProtocol)),
                            version = state.version,
                            metadata = updatedMetadata.toMetadata(),
                        )
                    responseMessage to newState
                }
            }

            OutboundSessionStatus.SentInitiatorHandshake -> {
                if (metadata.lastSendExpired(clock)) {
                    val updatedMetadata = metadata.copy(
                        commonData = metadata.commonData.copy(
                            lastSendTimestamp = clock.instant(),
                        ),
                    )
                    val initiatorHandshakeToResend =
                        stateConvertor.toCordaSessionState(
                            state,
                            sessionManagerImpl.revocationCheckerClient::checkRevocation,
                        ).message
                    val newState =
                        State(
                            key = state.key,
                            value = state.value,
                            version = state.version,
                            metadata = updatedMetadata.toMetadata(),
                        )
                    initiatorHandshakeToResend to newState
                } else {
                    null
                }
            }

            OutboundSessionStatus.SessionReady -> {
                logger.alreadySessionWarning(
                    message::class.java.simpleName,
                    message.responderHelloMessage.header.sessionId,
                )
                null
            }

            null -> {
                logger.noSessionWarning(message::class.java.simpleName, message.responderHelloMessage.header.sessionId)
                null
            }
        }
    }

    private data class ProcessHandshakeResult(
        val responseMessage: LinkOutMessage?,
        val stateToUpdate: State,
        val session: Session?,
    )

    private fun processInitiatorHandshake(
        state: State?,
        message: InboundSessionMessage.InitiatorHandshakeMessage,
    ): ProcessHandshakeResult? {
        val metadata = state?.metadata?.toInbound()
        return when (metadata?.status) {
            null -> {
                null
            }
            InboundSessionStatus.SentResponderHello -> {
                val sessionData =
                    stateConvertor.toCordaSessionState(
                        state,
                        sessionManagerImpl.revocationCheckerClient::checkRevocation,
                    ).sessionData as? AuthenticationProtocolResponder
                if (sessionData == null) {
                    logger.warn(
                        "Session ${state.key} has status SentResponderHello by the saved data is" +
                            " not AuthenticationProtocolResponder.",
                    )
                    return null
                }
                sessionManagerImpl.processInitiatorHandshake(sessionData, message.initiatorHandshakeMessage)?.let { responseMessage ->
                    val timestamp = clock.instant()
                    val newMetadata =
                        InboundSessionMetadata(
                            commonData = CommonMetadata(
                                source = responseMessage.header.sourceIdentity.toCorda(),
                                destination = responseMessage.header.destinationIdentity.toCorda(),
                                lastSendTimestamp = timestamp,
                                expiry = timestamp + SESSION_VALIDITY_PERIOD,
                            ),
                            status = InboundSessionStatus.SentResponderHandshake,
                        )
                    val session = sessionData.getSession()
                    val newState =
                        State(
                            message.initiatorHandshakeMessage.header.sessionId,
                            stateConvertor.toStateByteArray(SessionState(responseMessage, session)),
                            version = state.version,
                            metadata = newMetadata.toMetadata(),
                        )
                    ProcessHandshakeResult(responseMessage, newState, session)
                }
            }
            InboundSessionStatus.SentResponderHandshake -> {
                if (metadata.lastSendExpired(clock)) {
                    val timestamp = clock.instant()
                    val updatedMetadata = metadata.copy(
                        commonData = metadata.commonData.copy(
                            lastSendTimestamp = timestamp,
                        ),
                    )
                    val responderHandshakeToResend =
                        stateConvertor.toCordaSessionState(
                            state,
                            sessionManagerImpl.revocationCheckerClient::checkRevocation,
                        ).message
                    val newState =
                        State(
                            key = state.key,
                            value = state.value,
                            version = state.version,
                            metadata = updatedMetadata.toMetadata(),
                        )
                    ProcessHandshakeResult(responderHandshakeToResend, newState, null)
                } else {
                    null
                }
            }
        }
    }

    private fun processResponderHandshake(
        state: State?,
        message: OutboundSessionMessage.ResponderHandshakeMessage,
    ): ProcessHandshakeResult? {
        val metadata = state?.metadata?.toOutbound()
        return when (metadata?.status) {
            OutboundSessionStatus.SentInitiatorHandshake -> {
                val sessionState =
                    stateConvertor.toCordaSessionState(
                        state,
                        sessionManagerImpl.revocationCheckerClient::checkRevocation,
                    ).sessionData as AuthenticationProtocolInitiator
                val counterparties = state.getSessionCounterparties()

                sessionManagerImpl.processResponderHandshake(
                    message.responderHandshakeMessage,
                    counterparties,
                    sessionState,
                )?.let { session ->
                    val updatedMetadata =
                        metadata.copy(
                            status = OutboundSessionStatus.SessionReady,
                            commonData = metadata.commonData.copy(
                                lastSendTimestamp = clock.instant(),
                            ),
                        )
                    val newState =
                        State(
                            calculateOutboundSessionKey(
                                counterparties.ourId,
                                counterparties.counterpartyId,
                                counterparties.serial,
                            ),
                            stateConvertor.toStateByteArray(SessionState(null, session)),
                            version = state.version,
                            metadata = updatedMetadata.toMetadata(),
                        )
                    ProcessHandshakeResult(null, newState, session)
                }
            }

            OutboundSessionStatus.SentInitiatorHello -> {
                logger.error(
                    "Received ${message::class.java.simpleName} with session ID ${metadata.sessionId} but the corresponding pending " +
                        "session with this ID has an unexpected status ${metadata.status}",
                )
                null
            }

            OutboundSessionStatus.SessionReady -> {
                logger.alreadySessionWarning(
                    message::class.java.simpleName,
                    message.responderHandshakeMessage.header.sessionId,
                )
                null
            }

            null -> {
                logger.noSessionWarning(
                    message::class.java.simpleName,
                    message.responderHandshakeMessage.header.sessionId,
                )
                null
            }
        }
    }

    private fun <T> Any.getSessionIdIfInboundSessionMessage(trace: T): InboundSessionMessageContext<T>? {
        return when (this) {
            is AvroInitiatorHelloMessage ->
                InboundSessionMessageContext(
                    this.header!!.sessionId,
                    InboundSessionMessage.InitiatorHelloMessage(
                        this,
                    ),
                    trace,
                )
            is AvroInitiatorHandshakeMessage ->
                InboundSessionMessageContext(
                    this.header!!.sessionId,
                    InboundSessionMessage.InitiatorHandshakeMessage(
                        this,
                    ),
                    trace,
                )
            else -> null
        }
    }

    private fun <T> Any.getSessionIdIfOutboundSessionMessage(trace: T): OutboundSessionMessageContext<T>? {
        return when (this) {
            is AvroResponderHelloMessage ->
                OutboundSessionMessageContext(
                    this.header!!.sessionId,
                    OutboundSessionMessage.ResponderHelloMessage(this),
                    trace,
                )

            is AvroResponderHandshakeMessage ->
                OutboundSessionMessageContext(
                    this.header!!.sessionId,
                    OutboundSessionMessage.ResponderHandshakeMessage(this),
                    trace,
                )

            else -> null
        }
    }

    private fun State.getSessionCounterparties(): SessionManager.SessionCounterparties {
        val metadata = this.metadata.toOutbound()
        return SessionManager.SessionCounterparties(
            metadata.commonData.source,
            metadata.commonData.destination,
            metadata.membershipStatus,
            metadata.serial,
            metadata.communicationWithMgm,
        )
    }

    override val dominoTile =
        ComplexDominoTile(
            this::class.java.simpleName,
            coordinatorFactory,
            dependentChildren =
            setOf(
                stateManager.name,
                sessionManagerImpl.dominoTile.coordinatorName,
                LifecycleCoordinatorName.forComponent<SessionEncryptionOpsClient>(),
            ),
            managedChildren =
            setOf(
                sessionManagerImpl.dominoTile.toNamedLifecycle(),
            ),
        )
}
