package net.corda.flow.pipeline.handlers.requests.sessions

import net.corda.data.flow.state.waiting.SessionData
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.data.flow.state.waiting.Wakeup
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
import net.corda.flow.pipeline.handlers.requests.sessions.service.GenerateSessionService
import net.corda.flow.session.SessionManager
import net.corda.flow.session.SessionManagerFactory
import net.corda.libs.configuration.helper.getConfig
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.ConfigKeys.STATE_MANAGER_CONFIG
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowRequestHandler::class])
class ReceiveRequestHandler @Activate constructor(
    @Reference(service = GenerateSessionService::class)
    private val generateSessionService: GenerateSessionService,
    @Reference(service = SessionManagerFactory::class)
    private val sessionManagerFactory: SessionManagerFactory
) : FlowRequestHandler<FlowIORequest.Receive> {

    override val type = FlowIORequest.Receive::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.Receive): WaitingFor {
//        return WaitingFor(Wakeup())
        return WaitingFor(SessionData(request.sessions.map { it.sessionId }))
    }

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.Receive): FlowEventContext<Any> {
        val sessionManager = sessionManagerFactory.create(
            context.configs.getConfig(STATE_MANAGER_CONFIG),
            context.configs.getConfig(MESSAGING_CONFIG)
        )
        val missingSessions = request.sessions.filter { sessionManager.checkSessionExists(it.sessionId) }
        val configs = missingSessions.associate {
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
        sessionManager.close()

        //generate init messages for sessions which do not exist yet
//        generateSessionService.generateSessions(context, request.sessions, true)
        return context
    }
}
