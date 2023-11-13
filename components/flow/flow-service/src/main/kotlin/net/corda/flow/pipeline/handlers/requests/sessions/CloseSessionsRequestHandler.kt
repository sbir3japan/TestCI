package net.corda.flow.pipeline.handlers.requests.sessions

import net.corda.data.flow.state.waiting.SessionConfirmation
import net.corda.data.flow.state.waiting.SessionConfirmationType
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
import net.corda.flow.pipeline.handlers.requests.sessions.service.CloseSessionService
import net.corda.flow.session.SessionManagerFactory
import net.corda.libs.configuration.helper.getConfig
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.ConfigKeys.STATE_MANAGER_CONFIG
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowRequestHandler::class])
class CloseSessionsRequestHandler @Activate constructor(
//    @Reference(service = CloseSessionService::class)
//    private val closeSessionService: CloseSessionService,
    @Reference(service = SessionManagerFactory::class)
    private val sessionManagerFactory: SessionManagerFactory
) : FlowRequestHandler<FlowIORequest.CloseSessions> {

    override val type = FlowIORequest.CloseSessions::class.java

//    private fun getSessionsToClose(request: FlowIORequest.CloseSessions): List<String> {
//        return request.sessions.toList()
//    }

    override fun getUpdatedWaitingFor(
        context: FlowEventContext<Any>,
        request: FlowIORequest.CloseSessions
    ): WaitingFor {
        return WaitingFor(net.corda.data.flow.state.waiting.Wakeup())
//        val sessionsToClose = try {
//            closeSessionService.getSessionsToCloseForWaitingFor(context.checkpoint, getSessionsToClose(request))
//        } catch (e: Exception) {
//            val msg = e.message ?: "An error occurred in the platform - A session in ${request.sessions} was missing from the checkpoint"
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
        request: FlowIORequest.CloseSessions
    ): FlowEventContext<Any> {
        val sessionManager = sessionManagerFactory.create(
            context.configs.getConfig(STATE_MANAGER_CONFIG),
            context.configs.getConfig(MESSAGING_CONFIG)
        )
        try {
            request.sessions.forEach {
                sessionManager.deleteSession(it)
            }
        } catch (e: Exception) {
            throw FlowFatalException("Failed to delete sessions with IDs ${request.sessions}", e)
        } finally {
            sessionManager.close()
        }
        return context
//        val checkpoint = context.checkpoint
//        try {
//            closeSessionService.handleCloseForSessions(checkpoint, getSessionsToClose(request))
//        } catch (e: Exception) {
//            // TODO CORE-4850 Wakeup with error when session does not exist
//            val msg = e.message ?: "An error occurred in the platform - A session in ${request.sessions} was missing from the checkpoint"
//            throw FlowFatalException(msg, e)
//        }
//        return context
    }
}
