package net.corda.flow.pipeline.handlers.requests.sessions

import net.corda.data.flow.state.waiting.SessionData
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
import net.corda.flow.pipeline.handlers.requests.sessions.service.GenerateSessionService
import net.corda.flow.session.SessionManager
import net.corda.flow.session.SessionManagerFactory
import net.corda.libs.configuration.helper.getConfig
import net.corda.schema.configuration.ConfigKeys
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowRequestHandler::class])
class SendAndReceiveRequestHandler @Activate constructor(
//    @Reference(service = FlowSessionManager::class)
//    private val flowSessionManager: FlowSessionManager,
    @Reference(service = GenerateSessionService::class)
    private val generateSessionService: GenerateSessionService,
    @Reference(service = SessionManagerFactory::class)
    private val sessionManagerFactory: SessionManagerFactory
) : FlowRequestHandler<FlowIORequest.SendAndReceive> {

    override val type = FlowIORequest.SendAndReceive::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.SendAndReceive): WaitingFor {
        return WaitingFor(SessionData(request.sessionToInfo.map { it.key.sessionId }))
    }

    override fun postProcess(
        context: FlowEventContext<Any>,
        request: FlowIORequest.SendAndReceive
    ): FlowEventContext<Any> {
        val sessionManager = sessionManagerFactory.create(
            context.configs.getConfig(ConfigKeys.STATE_MANAGER_CONFIG),
            context.configs.getConfig(ConfigKeys.MESSAGING_CONFIG)
        )
        val configs = request.sessionToInfo.keys.associate {
            val sessionProperties = generateSessionService.createSessionProperties(context, it)
            it.sessionId to SessionManager.SessionConfig(
                cpiId = context.checkpoint.flowStartContext.cpiId,
                party = context.checkpoint.holdingIdentity,
                counterparty = HoldingIdentity(it.counterparty, context.checkpoint.holdingIdentity.groupId),
                requireClose = it.requireClose,
                contextSessionProperties = sessionProperties.avro
            )
        }
        configs.forEach {
            sessionManager.createSession(it.key, it.value)
        }
        request.sessionToInfo.forEach {
            sessionManager.sendMessage(it.key.sessionId, it.value)
        }
        sessionManager.close()
//        val checkpoint = context.checkpoint
//
//        //generate init messages for sessions which do not exist yet
//        generateSessionService.generateSessions(context, request.sessionToInfo.keys)
//
//        try {
//            checkpoint.putSessionStates(flowSessionManager.sendDataMessages(checkpoint, request.sessionToInfo, Instant.now()))
//        } catch (e: FlowSessionStateException) {
//            throw FlowPlatformException("Failed to send/receive: ${e.message}. $PROTOCOL_MISMATCH_HINT", e)
//        }

        return context
    }
}
