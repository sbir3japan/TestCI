package net.corda.processors.messagepattern.internal

import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.subscription.SubscriptionBase
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

@Component(service = [MessagePatternFactory::class])
class MessagePatternFactory @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = ProcessorFactory::class)
    private val processorFactory: ProcessorFactory,
) {

    companion object {
        private val clientIdCounter = AtomicInteger()
    }

    fun createSubscription(patternConfig: ResolvedPatternConfig, messagingConfig: SmartConfig): List<SubscriptionBase> {
        return when (patternConfig.type) {
            PatternType.DURABLE -> createDurableSubscription(patternConfig, messagingConfig)
            PatternType.STATEANDEVENT -> createStateAndEventSubscription(patternConfig, messagingConfig)
            PatternType.RPC -> createRPCSubscription(patternConfig, messagingConfig)
        }
    }

    private fun createRPCSubscription(patternConfig: ResolvedPatternConfig, messagingConfig: SmartConfig): List<SubscriptionBase> {
        val subscriptions = mutableListOf<SubscriptionBase>()
        val clientID = UUID.randomUUID().toString()

        repeat(patternConfig.count) {
            subscriptions.add(
                subscriptionFactory.createRPCSubscription(
                    getRPCConfig(patternConfig, clientID),
                    messagingConfig,
                    processorFactory.createRPCProcessor(patternConfig)
                )
            )
        }

        return subscriptions
    }

    private fun getRPCConfig(patternConfig: ResolvedPatternConfig, clientID: String) =
        RPCConfig(
            patternConfig.group,
            "$clientID-${clientIdCounter.getAndIncrement()}",
            patternConfig.topic,
            String::class.java,
            String::class.java
        )

    private fun getSubscriptionConfig(patternConfig: ResolvedPatternConfig) =
        SubscriptionConfig(
            patternConfig.group,
            patternConfig.topic
        )

    private fun createStateAndEventSubscription(patternConfig: ResolvedPatternConfig, messagingConfig: SmartConfig): List<SubscriptionBase> {
        val subscriptions = mutableListOf<SubscriptionBase>()

        repeat(patternConfig.count) {
            subscriptions.add(
                subscriptionFactory.createStateAndEventSubscription(
                    getSubscriptionConfig(patternConfig),
                    processorFactory.createStateAndEventProcessor(patternConfig),
                    messagingConfig
                )
            )
        }

        return subscriptions
    }

    private fun createDurableSubscription(patternConfig: ResolvedPatternConfig, messagingConfig: SmartConfig): List<SubscriptionBase> {
        val subscriptions = mutableListOf<SubscriptionBase>()

        repeat(patternConfig.count) {
            subscriptions.add(
                subscriptionFactory.createDurableSubscription(
                    getSubscriptionConfig(patternConfig),
                    processorFactory.createDurableProcessor(patternConfig),
                    messagingConfig,
                    null
                )
            )
        }

        return subscriptions
    }
}