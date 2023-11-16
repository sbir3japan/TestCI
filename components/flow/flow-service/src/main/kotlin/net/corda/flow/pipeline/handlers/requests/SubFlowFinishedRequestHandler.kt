package net.corda.flow.pipeline.handlers.requests

import net.corda.data.flow.state.waiting.SessionConfirmation
import net.corda.data.flow.state.waiting.SessionConfirmationType
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.handlers.requests.sessions.service.CloseSessionService
import net.corda.flow.session.SessionManagerFactory
import net.corda.libs.configuration.helper.getConfig
import net.corda.schema.configuration.ConfigKeys
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowRequestHandler::class])
class SubFlowFinishedRequestHandler @Activate constructor(
//    @Reference(service = CloseSessionService::class)
//    private val closeSessionService: CloseSessionService,
    @Reference(service = SessionManagerFactory::class)
    private val sessionManagerFactory: SessionManagerFactory
) : FlowRequestHandler<FlowIORequest.SubFlowFinished> {

    override val type = FlowIORequest.SubFlowFinished::class.java

//    private fun getSessionsToClose(request: FlowIORequest.SubFlowFinished): List<String> {
//        return request.sessionIds.toMutableList()
//    }

    override fun getUpdatedWaitingFor(
        context: FlowEventContext<Any>,
        request: FlowIORequest.SubFlowFinished
    ): WaitingFor {
        return WaitingFor(net.corda.data.flow.state.waiting.Wakeup())
//        val sessionsToClose = try {
//            closeSessionService.getSessionsToCloseForWaitingFor(context.checkpoint, getSessionsToClose(request))
//        } catch (e: Exception) {
//            val msg = e.message ?: "An error occurred in the platform - A session in ${request.sessionIds} was missing from the checkpoint"
//            throw FlowFatalException(msg, e)
//        }
//
//        return if (sessionsToClose.isEmpty()) {
//            WaitingFor(net.corda.data.flow.state.waiting.Wakeup())
//        } else {
//            WaitingFor(SessionConfirmation(sessionsToClose, SessionConfirmationType.CLOSE))
//        }
    }

    override fun postProcess(
        context: FlowEventContext<Any>,
        request: FlowIORequest.SubFlowFinished
    ): FlowEventContext<Any> {
        val sessionManager = sessionManagerFactory.create(
            context.configs.getConfig(ConfigKeys.STATE_MANAGER_CONFIG),
            context.configs.getConfig(ConfigKeys.MESSAGING_CONFIG)
        )
        val (initiatedSessions, initiatingSessions) = request.sessionIds.partition {
            it.endsWith("-INITIATED")
        }
        initiatedSessions.forEach {
            sessionManager.deleteSession(it)
        }
        initiatingSessions.forEach {
            sessionManager.deleteSession(it)
        }
//        val checkpoint = context.checkpoint
//        try {
//            closeSessionService.handleCloseForSessions(checkpoint, getSessionsToClose(request))
//        } catch (e: Exception) {
//            // TODO CORE-4850 Wakeup with error when session does not exist
//            val msg = e.message ?: "An error occurred in the platform - A session in ${request.sessionIds} was missing from the checkpoint"
//            throw FlowFatalException(msg, e)
//        }
        return context
    }
}