package net.corda.processors.messagepattern.internal

import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.processors.messagepattern.internal.processors.DurableProcessorImpl
import net.corda.processors.messagepattern.internal.processors.RPCResponderProcessorImpl
import net.corda.processors.messagepattern.internal.processors.StateAndEventProcessorImpl
import org.osgi.service.component.annotations.Component

@Component(service = [ProcessorFactory::class])
class ProcessorFactory {

    fun createDurableProcessor(resolvedPatternConfig: ResolvedPatternConfig) : DurableProcessor<String, String> {
        return DurableProcessorImpl(resolvedPatternConfig)
    }

    fun createStateAndEventProcessor(resolvedPatternConfig: ResolvedPatternConfig) : StateAndEventProcessor<String, String, String> {
        return StateAndEventProcessorImpl(resolvedPatternConfig)

    }

    fun createRPCProcessor(resolvedPatternConfig: ResolvedPatternConfig) : RPCResponderProcessor<String, String> {
        return RPCResponderProcessorImpl(resolvedPatternConfig)
    }
}