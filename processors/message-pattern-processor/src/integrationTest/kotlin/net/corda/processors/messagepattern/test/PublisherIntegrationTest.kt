package net.corda.processors.messagepattern.test

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.messaging.topic.utils.TopicUtils
import net.corda.libs.messaging.topic.utils.factory.TopicUtilsFactory
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.processors.messagepattern.test.TopicTemplates.Companion.DURABLE_TOPIC1
import net.corda.processors.messagepattern.test.TopicTemplates.Companion.DURABLE_TOPIC1_TEMPLATE
import net.corda.utilities.concurrent.getOrThrow
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.framework.FrameworkUtil
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension
import java.util.Properties
import java.util.concurrent.TimeUnit

@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
class PublisherIntegrationTest {

    private lateinit var publisherConfig: PublisherConfig

    private companion object {
        const val CLIENT_ID = "client.id"
        private val smartConfigFactory = SmartConfigFactory.createWithoutSecurityServices()
        val TEST_CONFIG = getResourceConfig("kafka.test.conf")
        val BOOTSTRAP_SERVERS_VALUE = "localhost:9092"

        /**
         * Retrieve a resource from this bundle and convert it to a SmartConfig object.
         */
        private fun getResourceConfig(resource: String): SmartConfig {
            val url = FrameworkUtil.getBundle(this::class.java).getResource(resource)
                ?: throw CordaRuntimeException(
                    "Failed to get resource $resource from bundle"
                )
            val config = ConfigFactory.parseURL(url)
            return smartConfigFactory.create(config)
        }
    }

    @InjectService(timeout = 4000)
    lateinit var publisherFactory: PublisherFactory

    @InjectService(timeout = 4000)
    lateinit var topicUtilFactory: TopicUtilsFactory

    private lateinit var topicUtils: TopicUtils

    @BeforeEach
    @Disabled
    fun beforeEach() {
        topicUtils = topicUtilFactory.createTopicUtils(getKafkaProperties())
    }

    fun getKafkaProperties(): Properties {
        val kafkaProperties = Properties()
        kafkaProperties["bootstrap.servers"] = BOOTSTRAP_SERVERS_VALUE
        kafkaProperties[CLIENT_ID] = "test"
        return kafkaProperties
    }

    @AfterEach
    @Disabled
    fun afterEach() {
        topicUtils.close()
    }
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `publisher can publish records to partitions non-transactionally successfully`() {
        publisherConfig = PublisherConfig(CLIENT_ID)
        val publisher = publisherFactory.createPublisher(publisherConfig, TEST_CONFIG)

        val recordsWithPartitions = getStringRecords("flow.event", 5, 2).map { 1 to it }
        val futures = publisher.publishToPartition(recordsWithPartitions)
        futures.map { it.getOrThrow() }
        publisher.close()
    }

    fun getStringRecords(topic: String, recordCount: Int, keyCount: Int): List<Record<String, String>> {
        val records = mutableListOf<Record<String, String>>()
        for (i in 1..keyCount) {
            val key = "key$i"
            for (j in 1..recordCount) {
                records.add(Record(topic, key, j.toString()))
            }
        }
        return records
    }
}
