package net.corda.flow.pipeline.handlers.requests.sessions

import net.corda.data.flow.state.waiting.CounterPartyFlowInfo
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowPlatformException
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
import net.corda.flow.pipeline.handlers.requests.sessions.service.GenerateSessionService
import net.corda.flow.pipeline.handlers.waiting.sessions.PROTOCOL_MISMATCH_HINT
import net.corda.flow.pipeline.sessions.FlowSessionStateException
import net.corda.flow.session.SessionManager
import net.corda.flow.session.SessionManagerFactory
import net.corda.libs.configuration.helper.getConfig
import net.corda.schema.configuration.ConfigKeys
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * This class handles a request received by the Flow Engine from the flow fiber.
 * This [FlowIORequest.CounterPartyFlowInfo] request is used to get information about the counterparty which includes data such as the
 * protocol version running.
 * Sets the checkpoint as waiting for [CounterPartyFlowInfo].
 * If the session has not been initiated yet (i.e no SessionInit sent yet to counterparty) then call the [GenerateSessionService] to
 * trigger SessionInitiation. This will allow us to receive flow information from the counterparty.
 * If the session was already initiated then the session data would already be present in the checkpoint and would have been accessed by
 * the flow fiber from the flow checkpoint.
 */
@Component(service = [FlowRequestHandler::class])
class CounterPartyFlowInfoRequestHandler @Activate constructor(
    @Reference(service = GenerateSessionService::class)
    private val generateSessionService: GenerateSessionService,
    @Reference(service = SessionManagerFactory::class)
    private val sessionManagerFactory: SessionManagerFactory
) : FlowRequestHandler<FlowIORequest.CounterPartyFlowInfo> {

    override val type = FlowIORequest.CounterPartyFlowInfo::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.CounterPartyFlowInfo): WaitingFor {
        return WaitingFor(CounterPartyFlowInfo(request.sessionInfo.sessionId))
    }

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.CounterPartyFlowInfo): FlowEventContext<Any> {
        try {
            val sessionManager = sessionManagerFactory.create(
                context.configs.getConfig(ConfigKeys.STATE_MANAGER_CONFIG),
                context.configs.getConfig(ConfigKeys.MESSAGING_CONFIG)
            )
            if (!sessionManager.checkSessionExists(request.sessionInfo.sessionId)) {
                val sessionProperties = generateSessionService.createSessionProperties(context, request.sessionInfo)
                val config = SessionManager.SessionConfig(
                    cpiId = context.checkpoint.flowStartContext.cpiId,
                    party = context.checkpoint.holdingIdentity,
                    counterparty = HoldingIdentity(request.sessionInfo.counterparty, context.checkpoint.holdingIdentity.groupId),
                    requireClose = request.sessionInfo.requireClose,
                    contextSessionProperties = sessionProperties.avro
                )
                sessionManager.createSession(request.sessionInfo.sessionId, config)
            }
            sessionManager.close()
//            val sessionInfo = request.sessionInfo
//            generateSessionService.generateSessions(context, setOf(sessionInfo), true)
        } catch (e: FlowSessionStateException) {
            throw FlowPlatformException("Failed to send: ${e.message}. $PROTOCOL_MISMATCH_HINT", e)
        }

        return context.copy(outputRecords = context.outputRecords)
    }
}
