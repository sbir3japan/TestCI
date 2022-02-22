package net.corda.flow.manager.impl.factory

import net.corda.data.flow.event.StartFlow
import net.corda.flow.manager.factory.FlowFactory
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.application.flows.Flow
import net.corda.v5.base.util.uncheckedCast
import org.osgi.service.component.annotations.Component

@Component(service = [FlowFactory::class])
@Suppress("Unused")
class FlowFactoryImpl : FlowFactory {

    override fun createFlow(startFlowEvent: StartFlow, sandboxGroupContext: SandboxGroupContext): Flow<*> {
        val flowClass: Class<Flow<*>> =
            uncheckedCast(
                sandboxGroupContext.sandboxGroup.loadClassFromMainBundles(
                    startFlowEvent.startContext.flowClassName,
                    Flow::class.java
                )
            )

        return flowClass
            .getDeclaredConstructor(String::class.java)
            .newInstance(startFlowEvent.flowStartArgs)
    }
}

