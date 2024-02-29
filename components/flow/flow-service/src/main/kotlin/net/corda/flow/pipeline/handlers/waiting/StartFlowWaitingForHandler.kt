package net.corda.flow.pipeline.handlers.waiting

import net.corda.data.flow.state.waiting.start.WaitingForStartFlow
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.pipeline.events.FlowEventContext
import org.osgi.service.component.annotations.Component
import org.slf4j.LoggerFactory

@Component(service = [FlowWaitingForHandler::class])
class StartFlowWaitingForHandler : FlowWaitingForHandler<WaitingForStartFlow> {

    override val type = WaitingForStartFlow::class.java
    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }
    override fun runOrContinue(context: FlowEventContext<*>, waitingFor: WaitingForStartFlow): FlowContinuation {
        log.info("StartFlowWaitingForHandler - Flow [${context.checkpoint.flowId}] StartFlowWaitingForHandler")

        return FlowContinuation.Run(Unit)
    }
}
