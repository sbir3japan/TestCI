package net.corda.processors.messagepattern.internal.impl

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.getStringOrDefault
import net.corda.libs.configuration.merger.ConfigMerger
import net.corda.messaging.api.exception.CordaMessageAPIConfigException
import net.corda.messaging.api.subscription.SubscriptionBase
import net.corda.processors.messagepattern.MessagePatternProcessor
import net.corda.processors.messagepattern.internal.MessagePatternFactory
import net.corda.processors.messagepattern.internal.PatternType
import net.corda.processors.messagepattern.internal.RecordSizeType
import net.corda.processors.messagepattern.internal.ResolvedPatternConfig
import net.corda.utilities.debug
import net.corda.utilities.detailedLogger
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Suppress("LongParameterList", "Unused")
@Component(service = [MessagePatternProcessor::class])
class MessagePatternProcessorImpl @Activate constructor(
    @Reference(service = ConfigMerger::class)
    private val configMerger: ConfigMerger,
    @Reference(service = MessagePatternFactory::class)
    private val messagePatternFactory: MessagePatternFactory,
): MessagePatternProcessor {

    private companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val DEFAULT_CONFIG_FILE = "default-messaging.conf"
        private const val BOOT_PATTERN = "pattern"
    }

    private val trackedSubscriptions = mutableListOf<SubscriptionBase>()

    override fun start(bootConfig: SmartConfig) {
        log.debug { "Message pattern processor starting with boot config.\n ${bootConfig.toSafeConfig().root().render()}" }
        val defaultMessagingConfig = getResourceConfig(DEFAULT_CONFIG_FILE, bootConfig.factory)
        log.debug { "Read default messaging config:\n ${defaultMessagingConfig.toSafeConfig().root().render()}" }
        val messagingConfig = configMerger.getMessagingConfig(bootConfig, defaultMessagingConfig)
        log.debug { "Merged messaging config with boot config:\n ${messagingConfig.toSafeConfig().root().render()}" }

        log.info ( "Boot Config:\n ${bootConfig.root().render()}" )
        val resolvedPatternConfig = resolve(bootConfig)
        log.info ( "Resolved Pattern Config: $resolvedPatternConfig" )

        if (resolvedPatternConfig.startupDelay > 0 ) {
            log.info("Delaying startup by [${resolvedPatternConfig.startupDelay}] seconds")
            Thread.sleep(resolvedPatternConfig.startupDelay.toLong())
        }
        val subscriptions = messagePatternFactory.createSubscription(resolvedPatternConfig, messagingConfig)
        trackedSubscriptions.addAll(subscriptions)
        log.info("Starting [${subscriptions.size}] subscriptions..." )
        subscriptions.onEach { it.start() }
        log.info("Started [${subscriptions.size}] subscriptions!" )
    }

    private fun resolve(bootConfig: SmartConfig): ResolvedPatternConfig {
        val type = PatternType.valueOf(bootConfig.getStringOrDefault("$BOOT_PATTERN.type", "DURABLE"))
        val topic = bootConfig.getStringOrDefault("$BOOT_PATTERN.topic", "flow.event")
        val group = bootConfig.getStringOrDefault("$BOOT_PATTERN.group", "$type.Group")
        val outputTopic = bootConfig.getStringOrDefault("$BOOT_PATTERN.outputTopic", "p2p.out")
        val count = Integer.parseInt(bootConfig.getStringOrDefault("$BOOT_PATTERN.count", "1"))
        val processorDelay = Integer.parseInt(bootConfig.getStringOrDefault("$BOOT_PATTERN.processorDelay", "0"))
        val outputRecordCount = Integer.parseInt(bootConfig.getStringOrDefault("$BOOT_PATTERN.outputRecordCount", "1"))
        val outputRecordSize = RecordSizeType.valueOf(bootConfig.getStringOrDefault("$BOOT_PATTERN.outputRecordSize", "SMALL"))
        val stateAndEventIterations = Integer.parseInt(bootConfig.getStringOrDefault("$BOOT_PATTERN.stateAndEventIterations", "0"))
        val startupDelay = Integer.parseInt(bootConfig.getStringOrDefault("$BOOT_PATTERN.startupDelay", "0"))

        return ResolvedPatternConfig(group, topic, count, type, processorDelay, outputRecordCount, outputTopic, outputRecordSize, stateAndEventIterations, startupDelay)
    }

    override fun stop() {
        log.info("Closing [${trackedSubscriptions.size}] subscriptions..." )
        trackedSubscriptions.onEach { it.close() }
        log.info("Closed [${trackedSubscriptions.size}] subscriptions!" )
        log.info("Message pattern processor stopping.")
    }

    /**
     * Retrieve a resource from this bundle and convert it to a SmartConfig object.
     *
     * If this is running outside OSGi (e.g. a unit test) then fall back to standard Java classloader mechanisms.
     */
    private fun getResourceConfig(resource: String, smartConfigFactory: SmartConfigFactory): SmartConfig {
        val bundle = FrameworkUtil.getBundle(this::class.java)
        val url = bundle?.getResource(resource)
            ?: this::class.java.classLoader.getResource(resource)
            ?: throw CordaMessageAPIConfigException(
                "Failed to get resource $resource from DB bus implementation bundle"
            )
        val config = ConfigFactory.parseURL(url)
        return smartConfigFactory.create(config)
    }
}



