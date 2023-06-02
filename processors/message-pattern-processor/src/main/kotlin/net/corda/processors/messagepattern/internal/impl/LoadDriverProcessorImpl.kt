package net.corda.processors.messagepattern.internal.impl

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.getStringOrDefault
import net.corda.libs.configuration.merger.ConfigMerger
import net.corda.messaging.api.exception.CordaMessageAPIConfigException
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.processors.messagepattern.LoadDriverProcessor
import net.corda.processors.messagepattern.internal.ResolvedLoadDriverConfig
import net.corda.processors.messagepattern.internal.RecordSizeType
import net.corda.processors.messagepattern.internal.SenderType
import net.corda.processors.messagepattern.internal.processors.generateOutputRecord
import net.corda.processors.messagepattern.internal.processors.generateValue
import net.corda.utilities.debug
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

@Suppress("LongParameterList", "Unused")
@Component(service = [LoadDriverProcessor::class])
class LoadDriverProcessorImpl @Activate constructor(
    @Reference(service = ConfigMerger::class)
    private val configMerger: ConfigMerger,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
): LoadDriverProcessor {

    private companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val DEFAULT_CONFIG_FILE = "default-messaging.conf"
        private const val BOOT_PATTERN = "pattern"
    }

    override fun start(bootConfig: SmartConfig) {
        log.debug { "Load Driver processor starting with boot config.\n ${bootConfig.toSafeConfig().root().render()}" }
        val defaultMessagingConfig = getResourceConfig(DEFAULT_CONFIG_FILE, bootConfig.factory)
        log.debug { "Read default messaging config:\n ${defaultMessagingConfig.toSafeConfig().root().render()}" }
        val messagingConfig = configMerger.getMessagingConfig(bootConfig, defaultMessagingConfig)
        log.debug { "Merged messaging config with boot config:\n ${messagingConfig.toSafeConfig().root().render()}" }

        val resolvedPatternConfig = resolve(bootConfig)
        log.info("Resolved Pattern Config: $resolvedPatternConfig")


        if (resolvedPatternConfig.type == SenderType.RPC) {
            val sender = publisherFactory.createRPCSender(RPCConfig(resolvedPatternConfig.group, "RPCClient", resolvedPatternConfig.outputTopic, String::class.java, String::class.java), messagingConfig)
            sender.start()
            runRPCSender(sender, resolvedPatternConfig)
        } else {
            val publisher = publisherFactory.createPublisher(PublisherConfig("loadDriverClientId", true), messagingConfig)
            runPublisher(publisher, resolvedPatternConfig)
        }
    }

    private fun runRPCSender(sender: RPCSender<String, String>, resolvedPatternConfig: ResolvedLoadDriverConfig) {
        val nextRun = Instant.now().toEpochMilli() + resolvedPatternConfig.startupDelay
        var counter = 0
        log.info("Starting to send records in [${nextRun - Instant.now().toEpochMilli()}] millis")
        while (counter < resolvedPatternConfig.count.toInt()) {
            if (Instant.now().toEpochMilli() > nextRun) {
                repeat(resolvedPatternConfig.outputRecordCount) {
                    sender.sendRequest(generateValue(resolvedPatternConfig.outputRecordSize))
                }
                counter += resolvedPatternConfig.outputRecordCount
                log.info("Sent [$counter] records so far")
            }
        }
    }

    private fun runPublisher(publisher: Publisher, resolvedPatternConfig: ResolvedLoadDriverConfig) {
        var nextRun = Instant.now().toEpochMilli() + resolvedPatternConfig.startupDelay
        var counter = 0
        log.info("Starting to send records in [${nextRun - Instant.now().toEpochMilli()}] millis")
        while (counter < resolvedPatternConfig.count.toInt()) {
            if (Instant.now().toEpochMilli() > nextRun) {
                val records = mutableListOf<Record<*, *>>()
                repeat(resolvedPatternConfig.outputRecordCount) {
                    records.add(
                            generateOutputRecord(
                                UUID.randomUUID().toString(),
                                resolvedPatternConfig.outputTopic,
                                resolvedPatternConfig.outputRecordSize
                            )
                        )

                }
                publisher.batchPublish(records)
                counter += resolvedPatternConfig.outputRecordCount
                nextRun = Instant.now().toEpochMilli() + resolvedPatternConfig.processorDelay
                log.info("Sent [$counter] records so far")
            }
        }
    }

    private fun resolve(bootConfig: SmartConfig): ResolvedLoadDriverConfig {
        val count = bootConfig.getStringOrDefault("$BOOT_PATTERN.count", "1000000")
        val outputTopic = bootConfig.getStringOrDefault("$BOOT_PATTERN.outputTopic", "flow.event")
        val processorDelay = Integer.parseInt(bootConfig.getStringOrDefault("$BOOT_PATTERN.processorDelay", "0"))
        val startupDelay = Integer.parseInt(bootConfig.getStringOrDefault("$BOOT_PATTERN.startupDelay", "0"))
        val outputRecordCount = Integer.parseInt(bootConfig.getStringOrDefault("$BOOT_PATTERN.outputRecordCount", "1000"))
        val outputRecordSize = RecordSizeType.valueOf(bootConfig.getStringOrDefault("$BOOT_PATTERN.outputRecordSize", "SMALL"))
        val senderType = SenderType.valueOf(bootConfig.getStringOrDefault("$BOOT_PATTERN.type", "PUBLISHER"))
        val group = bootConfig.getStringOrDefault("$BOOT_PATTERN.group", "RPCGroup")

        return ResolvedLoadDriverConfig(processorDelay, startupDelay, outputRecordCount, outputTopic, outputRecordSize, count, senderType, group)
    }

    override fun stop() {
        log.info("Load Driver processor stopping.")
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



