package net.corda.messaging.subscription

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.builder.CordaConsumerBuilder
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.constants.SubscriptionType
import net.corda.messaging.createResolvedSubscriptionConfig
import net.corda.messaging.generateMockCordaConsumerRecordList
import net.corda.messaging.stubs.StubPubSubProcessor
import net.corda.test.util.waitWhile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class PubSubSubscriptionImplTest {
    private companion object {
        private const val TEST_TIMEOUT_SECONDS = 30L
    }

    private var mockRecordCount = 5L
    private val config = createResolvedSubscriptionConfig(SubscriptionType.PUB_SUB)
    private val consumerPollAndProcessRetriesCount = config.processorRetries
    private val cordaConsumerBuilder: CordaConsumerBuilder = mock()
    private val mockCordaConsumer: CordaConsumer<String, ByteBuffer> = mock()
    private val mockConsumerRecords = generateMockCordaConsumerRecordList(mockRecordCount, "topic", 1)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock()
    private val lifeCycleCoordinatorMockHelper = LifeCycleCoordinatorMockHelper()

    private var pollInvocationCount: Int = 0
    private var builderInvocationCount: Int = 0
    private lateinit var kafkaPubSubSubscription: PubSubSubscriptionImpl<String, ByteBuffer>
    private lateinit var processor: PubSubProcessor<String, ByteBuffer>
    private lateinit var latch: CountDownLatch

    @BeforeEach
    fun setup() {
        latch = CountDownLatch(mockRecordCount.toInt())
        processor = StubPubSubProcessor(latch)

        pollInvocationCount = 0
        doAnswer {
            if (pollInvocationCount == 0) {
                pollInvocationCount++
                mockConsumerRecords
            } else {
                mutableListOf()
            }
        }.whenever(mockCordaConsumer).poll(config.pollTimeout)

        builderInvocationCount = 0
        doReturn(mockCordaConsumer).whenever(cordaConsumerBuilder).createConsumer<String, ByteBuffer>(
            any(),
            any(),
            any(),
            any(),
            any(),
            anyOrNull()
        )
        doReturn(lifeCycleCoordinatorMockHelper.lifecycleCoordinator).`when`(lifecycleCoordinatorFactory)
            .createCoordinator(any(), any())
    }

    /**
     * Test processor is executed when a new record is added to the consumer.
     */
    @Test
    fun testPubSubConsumer() {
        kafkaPubSubSubscription =
            PubSubSubscriptionImpl(
                config,
                cordaConsumerBuilder,
                processor,
                lifecycleCoordinatorFactory
            )
        kafkaPubSubSubscription.start()

        latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        kafkaPubSubSubscription.close()
        assertThat(latch.count).isEqualTo(0)
        verify(cordaConsumerBuilder, times(1)).createConsumer<String, ByteBuffer>(
            any(),
            any(),
            any(),
            any(),
            any(),
            anyOrNull()
        )
    }

    /**
     * Check that the exceptions thrown during building exits correctly
     */
    @Test
    fun testFatalExceptionConsumerBuild() {
        whenever(
            cordaConsumerBuilder.createConsumer<String, ByteBuffer>(
                any(),
                any(),
                any(),
                any(),
                any(),
                anyOrNull()
            )
        ).thenThrow(
            CordaMessageAPIFatalException(
                "Fatal Error",
                Exception()
            )
        )

        kafkaPubSubSubscription =
            PubSubSubscriptionImpl(
                config,
                cordaConsumerBuilder,
                processor,
                lifecycleCoordinatorFactory
            )

        kafkaPubSubSubscription.start()
        waitWhile(Duration.ofSeconds(TEST_TIMEOUT_SECONDS)) { kafkaPubSubSubscription.isRunning }

        verify(mockCordaConsumer, times(0)).poll(config.pollTimeout)
        verify(cordaConsumerBuilder, times(1)).createConsumer<String, ByteBuffer>(
            any(),
            any(),
            any(),
            any(),
            any(),
            anyOrNull()
        )
        assertThat(latch.count).isEqualTo(mockRecordCount)

        Assertions.assertFalse(lifeCycleCoordinatorMockHelper.lifecycleCoordinatorThrows)
    }

    /**
     * Check that the exceptions thrown during polling exits correctly
     */
    @Test
    fun testConsumerPollFailRetries() {
        doAnswer {
            if (builderInvocationCount == 0) {
                builderInvocationCount++
                mockCordaConsumer
            } else {
                CordaMessageAPIFatalException("Consumer Create Fatal Error", Exception())
            }
        }.whenever(cordaConsumerBuilder)
            .createConsumer<String, ByteBuffer>(any(), any(), any(), any(), any(), anyOrNull())
        whenever(mockCordaConsumer.poll(config.pollTimeout)).thenThrow(
            CordaMessageAPIFatalException(
                "Fatal Error",
                Exception()
            )
        )

        kafkaPubSubSubscription =
            PubSubSubscriptionImpl(
                config,
                cordaConsumerBuilder,
                processor,
                lifecycleCoordinatorFactory
            )

        kafkaPubSubSubscription.start()
        waitWhile(Duration.ofSeconds(TEST_TIMEOUT_SECONDS)) { kafkaPubSubSubscription.isRunning }

        assertThat(latch.count).isEqualTo(mockRecordCount)
        verify(cordaConsumerBuilder, times(2)).createConsumer<String, ByteBuffer>(
            any(),
            any(),
            any(),
            any(),
            any(),
            anyOrNull()
        )
        verify(mockCordaConsumer, times(consumerPollAndProcessRetriesCount + 1)).poll(config.pollTimeout)

        Assertions.assertFalse(lifeCycleCoordinatorMockHelper.lifecycleCoordinatorThrows)
    }

    /**
     * Check that the exceptions thrown during processing causes the processor to continue
     */
    @Test
    fun testIOExceptionExceptionDuringProcessing() {
        processor = StubPubSubProcessor(latch, IOException())
        doReturn(mockConsumerRecords).whenever(mockCordaConsumer).poll(config.pollTimeout)

        kafkaPubSubSubscription = PubSubSubscriptionImpl(
            config, cordaConsumerBuilder,
            processor, lifecycleCoordinatorFactory
        )
        kafkaPubSubSubscription.start()

        latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        kafkaPubSubSubscription.close()
        assertThat(latch.count).isEqualTo(0)
        verify(cordaConsumerBuilder, times(1)).createConsumer<String, ByteBuffer>(
            any(),
            any(),
            any(),
            any(),
            any(),
            anyOrNull()
        )
    }

    @Test
    fun `testPubSubConsumer if future results in IOException`() {
        processor = StubPubSubProcessor(latch, null, IOException())
        kafkaPubSubSubscription =
            PubSubSubscriptionImpl(
                config,
                cordaConsumerBuilder,
                processor,
                lifecycleCoordinatorFactory
            )
        kafkaPubSubSubscription.start()

        latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        kafkaPubSubSubscription.close()
        assertThat(latch.count).isEqualTo(0)
        verify(cordaConsumerBuilder, times(1)).createConsumer<String, ByteBuffer>(
            any(),
            any(),
            any(),
            any(),
            any(),
            anyOrNull()
        )
    }

    @Test
    fun testThrowablesCaughtBeforeThreadDefaultHandler() {
        val lock = ReentrantLock()
        var subscriptionThread: Thread? = null
        var uncaughtExceptionInSubscriptionThread: Throwable? = null

        doAnswer {
            lock.withLock {
                subscriptionThread = Thread.currentThread()
            }
            // Here's our chance to make sure there are no uncaught exceptions in this, the subscription thread
            subscriptionThread!!.setUncaughtExceptionHandler { _, e ->
                lock.withLock {
                    uncaughtExceptionInSubscriptionThread = e
                }
            }
            @Suppress("TooGenericExceptionThrown")
            throw Throwable()
        }.whenever(cordaConsumerBuilder)
            .createConsumer<String, ByteBuffer>(any(), any(), any(), any(), any(), anyOrNull())

        kafkaPubSubSubscription =
            PubSubSubscriptionImpl(
                config,
                cordaConsumerBuilder,
                processor,
                lifecycleCoordinatorFactory
            )

        kafkaPubSubSubscription.start()
        // We must wait for the callback above in order we know what thread to join below
        waitWhile(Duration.ofSeconds(TEST_TIMEOUT_SECONDS)) { lock.withLock { subscriptionThread == null } }
        subscriptionThread!!.join(TEST_TIMEOUT_SECONDS * 1000)
        Assertions.assertNull(lock.withLock { uncaughtExceptionInSubscriptionThread })
    }
}
