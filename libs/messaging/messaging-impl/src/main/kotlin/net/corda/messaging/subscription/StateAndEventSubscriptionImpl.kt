package net.corda.messaging.subscription

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.deadletter.StateAndEventDeadLetterRecord
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messaging.api.chunking.ChunkSerializerService
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.config.ResolvedSubscriptionConfig
import net.corda.messaging.constants.MetricsConstants
import net.corda.messaging.subscription.consumer.StateAndEventConsumer
import net.corda.messaging.subscription.consumer.builder.StateAndEventBuilder
import net.corda.messaging.subscription.consumer.listener.StateAndEventConsumerRebalanceListener
import net.corda.messaging.utils.getEventsByBatch
import net.corda.messaging.utils.toCordaProducerRecords
import net.corda.messaging.utils.toRecord
import net.corda.messaging.utils.tryGetResult
import net.corda.metrics.CordaMetrics
import net.corda.schema.Schemas.getDLQTopic
import net.corda.schema.Schemas.getStateAndEventStateTopic
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.time.Clock
import java.util.*

@Suppress("LongParameterList")
internal class StateAndEventSubscriptionImpl<K : Any, S : Any, E : Any>(
    private val config: ResolvedSubscriptionConfig,
    private val builder: StateAndEventBuilder,
    private val processor: StateAndEventProcessor<K, S, E>,
    private val cordaAvroSerializer: CordaAvroSerializer<Any>,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    private val chunkSerializerService: ChunkSerializerService,
    private val stateAndEventListener: StateAndEventListener<K, S>? = null,
    private val clock: Clock = Clock.systemUTC(),
) : StateAndEventSubscription<K, S, E> {

    private val log = LoggerFactory.getLogger("${this.javaClass.name}-${config.clientId}")

    private var nullableProducer: CordaProducer? = null
    private var nullableStateAndEventConsumer: StateAndEventConsumer<K, S, E>? = null
    private var nullableEventConsumer: CordaConsumer<K, E>? = null
    private var threadLooper =
        ThreadLooper(log, config, lifecycleCoordinatorFactory, "state/event processing thread", ::runConsumeLoop)

    private val producer: CordaProducer
        get() {
            return nullableProducer ?: throw IllegalStateException("Unexpected access to null producer.")
        }

    private val stateAndEventConsumer: StateAndEventConsumer<K, S, E>
        get() {
            return nullableStateAndEventConsumer
                ?: throw IllegalStateException("Unexpected access to null stateAndEventConsumer.")
        }

    private val eventConsumer: CordaConsumer<K, E>
        get() {
            return nullableEventConsumer ?: throw IllegalStateException("Unexpected access to null eventConsumer.")
        }

    private val eventTopic = config.topic
    private val stateTopic = getStateAndEventStateTopic(config.topic)
    private lateinit var deadLetterRecords: MutableList<ByteArray>

    private val errorMsg = "Failed to read and process records from topic $eventTopic, group ${config.group}, " +
            "producerClientId ${config.clientId}."

    private val processorMeter = CordaMetrics.Metric.MessageProcessorTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternType, MetricsConstants.STATE_AND_EVENT_PATTERN_TYPE)
        .withTag(CordaMetrics.Tag.MessagePatternClientId, config.clientId)
        .withTag(CordaMetrics.Tag.OperationName, MetricsConstants.ON_NEXT_OPERATION)
        .build()

    private val batchSizeHistogram = CordaMetrics.Metric.MessageBatchSize.builder()
        .withTag(CordaMetrics.Tag.MessagePatternType, MetricsConstants.STATE_AND_EVENT_PATTERN_TYPE)
        .withTag(CordaMetrics.Tag.MessagePatternClientId, config.clientId)
        .build()

    private val commitTimer = CordaMetrics.Metric.MessageCommitTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternType, MetricsConstants.STATE_AND_EVENT_PATTERN_TYPE)
        .withTag(CordaMetrics.Tag.MessagePatternClientId, config.clientId)
        .build()

    // Pipeline POC

    // processing variables that need to be tuned and taken from config:
//    private val processorChannelBufferSize = 500
    private val sendingChannelBufferSize = 500
//    private val commitApproxEveryMs = 50L


    // HACK setting event as nullable so that we can send "empty" events to ensure we commit at least once every x ms.
//    data class StateAndEventData<K: Any, S: Any, E: Any>(val response: StateAndEventProcessor.Response<S>, val event: CordaConsumerRecord<K, E>?, val metadata: Any?)
    data class StateAndEventData2<K: Any, S: Any, E: Any>(val outputRecords: List<CordaProducerRecord<*, *>>, val events: List<CordaConsumerRecord<K, E>>, val metadata: Any?)
//    data class CordaConsumerRecordAndMetadata<K: Any, E: Any>(val event: CordaConsumerRecord<K, E>, val metadata: Any)

    // channels used for messages to process
    // capacity of the channel buffer TBD - producing function will suspend when buffer full
    // number of channels should be configurable and indicate level of parallelism
//    private val processingChannels = (1..numberOfProcessorChannels).map { Channel<CordaConsumerRecord<K, E>>(processorChannelBufferSize) }

    // channel used for messages that need sending - buffer TBD
//    private val sendingChannel = Channel<StateAndEventData<K, S, E>>(sendingChannelBufferSize)
    private val sendingChannel2 = Channel<StateAndEventData2<K, S, E>>(sendingChannelBufferSize)
//    private val sendingChannels = ConcurrentHashMap<Int, Channel<StateAndEventData<K, S, E>>>()

//    private val processingChannels = ConcurrentHashMap<Int, Channel<CordaConsumerRecordAndMetadata<K, E>>>()
//    private val producers = ConcurrentHashMap<Int, CordaProducer>()

//    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class) // isEmpty is experimental
//    private fun CoroutineScope.messageHeartbeat() = launch {
//        val emptyResponse = StateAndEventProcessor.Response<S>(null, emptyList(), false)
//        flow {
//            while (true) {
//                // TODO: optimise this
//                delay(commitApproxEveryMs*2)
//                emit(Unit)
//            }
//        }.collect {
////            sendingChannels.values.forEach {
////                // only send heartbeat if the channel is empty
////                if (it.isEmpty) {
////                    log.info("Sending channel $it is empty, so sending a heartbeat message")
////                    it.send(StateAndEventData(emptyResponse, null, null))
////                }
////            }
//            if (sendingChannel2.isEmpty) {
//                log.info("Sending channel $it is empty, so sending a heartbeat message")
//                sendingChannel2.send(StateAndEventData(emptyResponse, null, null))
//            }
//        }
//    }

//    private fun CoroutineScope.messageProcessor(partitionId: Int, messages: ReceiveChannel<CordaConsumerRecordAndMetadata<K, E>>) = launch {
//        val sendingChannel = sendingChannels.computeIfAbsent(partitionId) {
//            log.info("Creating processing channel for partition $partitionId")
//            val channel = Channel<StateAndEventData<K, S, E>>(sendingChannelBufferSize)
//            messageSender(partitionId, channel)
//            channel
//        }
//        for (msg in messages) {
//            log.info("Processor for #$partitionId is processing ${msg.event.key} on ${Thread.currentThread().name}")
//
//            // simplistic interpretation of existing code
//            val key = msg.event.key
//            val state = stateAndEventConsumer.getInMemoryStateValue(key)
//            val thisEventUpdates = processor.onNext(state, msg.event.toRecord())
//            if(thisEventUpdates.markForDLQ) {
//                log.warn("TODO: handling DLQ")
//                continue
//            }
//            sendingChannel.send(StateAndEventData(thisEventUpdates, msg.event, msg.metadata))
//        }
//    }

//    private fun CoroutineScope.messageSender(
//        partitionId: Int,
//        messages: Channel<StateAndEventData<K, S, E>>,
//    ) = launch {
////        val channelProducer = producers.computeIfAbsent(partitionId) {
////            log.info("Creating producer for partition $partitionId")
////            val newConfig = config.copy(uniqueId = "${config.uniqueId}-$partitionId" )
////            builder.createProducer(newConfig) { data ->
////                log.warn("Failed to serialize record from ${config.topic}")
////                deadLetterRecords.add(data)
////            }
////        }
//        val channelProducer = producer
//
//        var beginTx = 0L
//        var txStarted = false
//        var msgsInCommit = 0
//        val eventsSinceCommit = mutableListOf<CordaConsumerRecord<*,*>>()
//        val updatedStates: MutableMap<Int, MutableMap<K, S?>> = mutableMapOf()
//        var meta: Any? = null
//
//        fun beginTx() {
//            log.info("[Sending Channel $partitionId] Begin tx")
//            beginTx = System.nanoTime()
//            channelProducer.beginTransaction()
//            txStarted = true
//        }
//
//        fun commitTx() {
//            log.info("[Sending Channel $partitionId] Commit tx - $msgsInCommit messages, ${eventsSinceCommit.size} events,  since last commit")
//            try {
//                channelProducer.sendRecordOffsetsToTransaction(eventsSinceCommit, meta)
//                channelProducer.commitTransaction()
//                stateAndEventConsumer.updateInMemoryStatePostCommit(updatedStates, clock)
//                msgsInCommit = 0
//                txStarted = false
//                eventsSinceCommit.clear()
//                updatedStates.clear()
//            }
//            catch (e: Exception) {
//                log.error("TODO: error handling", e)
//            }
//        }
//
//        for (msg in messages) {
//            if(null != msg.event) {
//                try {
//                    if (!txStarted) beginTx()
//
//                    val updatedState = msg.response.updatedState
//                    val outputRecords = mutableListOf<Record<*, *>>()
//                    outputRecords.addAll(msg.response.responseEvents)
//                    outputRecords.add(Record(stateTopic, msg.event.key, updatedState))
//
//                    msgsInCommit += outputRecords.size
//                    log.trace("[Sending Channel $partitionId] Sending $outputRecords")
//                    channelProducer.sendRecords(outputRecords.toCordaProducerRecords())
//                    eventsSinceCommit.add(msg.event)
//                    updatedStates.computeIfAbsent(msg.event.partition) { mutableMapOf() }[msg.event.key] = updatedState
//                    meta = msg.metadata
//                }
//                catch (e: Exception) {
//                    log.error("TODO: error handling", e)
//                }
//            }
//
//            if(txStarted && System.nanoTime() - beginTx > 1_000_000 * commitApproxEveryMs) commitTx()
//        }
//
//        // commit before exit.
//        if(txStarted) commitTx()
//    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun CoroutineScope.messageSender2(
        messages: Channel<StateAndEventData2<K, S, E>>,
    ) = launch(newSingleThreadContext("sending-thread")) {
        for(msg in messages) {
            log.info("Committing ${msg.outputRecords.size} records for ${msg.events.size} events on ${Thread.currentThread().name}")
            try {
                commitTimer.recordCallable {
                    producer.beginTransaction()
                    producer.sendRecords(msg.outputRecords)
                    if (deadLetterRecords.isNotEmpty()) {
                        producer.sendRecords(deadLetterRecords.map {
                            CordaProducerRecord(
                                getDLQTopic(eventTopic),
                                UUID.randomUUID().toString(),
                                it
                            )
                        })
                        deadLetterRecords.clear()
                    }
                    producer.sendRecordOffsetsToTransaction(msg.events, msg.metadata)
                    producer.commitTransaction()
                }
            }
            catch (e: Exception) {
                    log.error("TODO: error handling", e)
                }
        }
    }
    // ...

    /**
     * Is the subscription running.
     */
    val isRunning: Boolean
        get() = threadLooper.isRunning

    override val subscriptionName: LifecycleCoordinatorName
        get() = threadLooper.lifecycleCoordinatorName

    override fun start() {
        log.debug { "Starting subscription with config: $config" }
        threadLooper.start()
    }

    /**
     * This method is for closing the loop/thread externally. From inside the loop use the private [stopConsumeLoop].
     */
    override fun close() {
        threadLooper.close()
    }

    private fun runConsumeLoop() {
        var attempts = 0
        var nullableRebalanceListener: StateAndEventConsumerRebalanceListener? = null

        while (!threadLooper.loopStopped) {
            attempts++
            // TODO - not sure runBlocking launch and the above OptIn is correct
            runBlocking {
                try {
                    deadLetterRecords = mutableListOf()
                    nullableProducer = builder.createProducer(config) { data ->
                        log.warn("Failed to serialize record from ${config.topic}")
                        deadLetterRecords.add(data)
                    }
                    val (stateAndEventConsumerTmp, rebalanceListener) = builder.createStateEventConsumerAndRebalanceListener(
                        config,
                        processor.keyClass,
                        processor.stateValueClass,
                        processor.eventValueClass,
                        stateAndEventListener,
                        { data ->
                            log.error("Failed to deserialize state record from $stateTopic")
                            deadLetterRecords.add(data)
                        },
                        { data ->
                            log.error("Failed to deserialize event record from $eventTopic")
                            deadLetterRecords.add(data)
                        }
                    )
                    nullableRebalanceListener = rebalanceListener
                    val eventConsumerTmp = stateAndEventConsumerTmp.eventConsumer
                    nullableStateAndEventConsumer = stateAndEventConsumerTmp
                    nullableEventConsumer = eventConsumerTmp
                    eventConsumerTmp.subscribe(eventTopic, rebalanceListener)
                    threadLooper.updateLifecycleStatus(LifecycleStatus.UP)

                    // hacky of ensuring we have a commit at least once every 50 ms
//                    messageHeartbeat()

                    // fan into single sending channel, otherwise we need multiple producers,
                    messageSender2(sendingChannel2)

                    while (!threadLooper.loopStopped) {
                        processBatchOfEvents()
//                        stateAndEventConsumerTmp.pollAndUpdateStates(true)
//                        val records = stateAndEventConsumer.pollEvents()
//                        if (records.isNotEmpty()) {
//                            log.info("${records.size} records returned from Kafka poll. Using partitions ${records.map { it.partition }}")
//                            for (event in records) {
//                                val processingChannel = processingChannels.computeIfAbsent(event.partition) {
//                                    // lazily create processing channel and wire up processor
//                                    log.info("Creating processing channel for partition ${event.partition}")
//                                    Channel<CordaConsumerRecordAndMetadata<K, E>>(processorChannelBufferSize).also {
//                                        messageProcessor(event.partition, it)
//                                    }
//                                }
                                // yuk!!
                                // Using Any type and cast later so not to expose the Kafka type.
                                //  this needs to be wrapped.
//                                val meta = stateAndEventConsumer.eventConsumer.kafkaGroupMetadata()
//                                processingChannel.send(CordaConsumerRecordAndMetadata(event, meta))
//                                sendingChannel.send(StateAndEventData(thisEventUpdates, event, meta))
//                            }
//                        }
                    }
                } catch (ex: Exception) {
                    when (ex) {
                        is CordaMessageAPIIntermittentException -> {
                            log.warn(
                                "$errorMsg Attempts: $attempts. Recreating " +
                                        "consumer/producer and Retrying.", ex
                            )
                        }

                        else -> {
                            log.error(
                                "$errorMsg Attempts: $attempts. Closing subscription.", ex
                            )
                            threadLooper.updateLifecycleStatus(LifecycleStatus.ERROR, errorMsg)
                            threadLooper.stopLoop()
                        }
                    }
                } finally {
                    closeStateAndEventProducerConsumer()
                    coroutineContext.cancelChildren()
                }
            }
        }
        nullableRebalanceListener?.close()
        closeStateAndEventProducerConsumer()
    }

    private fun closeStateAndEventProducerConsumer() {
        nullableProducer?.close()
        nullableStateAndEventConsumer?.close()
        nullableProducer = null
        nullableStateAndEventConsumer = null
    }

    private suspend fun processBatchOfEvents() {
        var attempts = 0
        var keepProcessing = true
        while (keepProcessing && !threadLooper.loopStopped) {
            try {
                log.debug { "Polling and processing events" }
                var rebalanceOccurred = false
                val records = stateAndEventConsumer.pollEvents()
                batchSizeHistogram.record(records.size.toDouble())
                val batches = getEventsByBatch(records).iterator()
                while (!rebalanceOccurred && batches.hasNext()) {
                    val batch = batches.next()
                    rebalanceOccurred = tryProcessBatchOfEvents(batch)
                }
                keepProcessing = false // We only want to do one batch at a time
            } catch (ex: Exception) {
                when (ex) {
                    is CordaMessageAPIIntermittentException -> {
                        attempts++
                        handleProcessEventRetries(attempts, ex)
                    }

                    else -> {
                        throw CordaMessageAPIFatalException(
                            "Failed to process records from topic $eventTopic, group ${config.group}, " +
                                    "producerClientId ${config.clientId}. " +
                                    "Fatal error occurred.", ex
                        )
                    }
                }
            }
        }
    }

    /**
     * Process a batch of events from the last poll and publish the outputs (including DLQd events)
     *
     * @return false if the batch had to be abandoned due to a rebalance
     */
    private suspend fun tryProcessBatchOfEvents(events: List<CordaConsumerRecord<K, E>>): Boolean {
            val outputRecords = mutableListOf<Record<*, *>>()
            val updatedStates: MutableMap<Int, MutableMap<K, S?>> = mutableMapOf()
            // Pre-populate the updated states with the current in-memory state.
            events.forEach {
                val partitionMap = updatedStates.computeIfAbsent(it.partition) { mutableMapOf() }
                partitionMap.computeIfAbsent(it.key) { key ->
                    stateAndEventConsumer.getInMemoryStateValue(key)
                }
            }

            log.debug { "Processing events(keys: ${events.joinToString { it.key.toString() }}, size: ${events.size})" }
            try {
                processorMeter.recordCallable {
                    for (event in events) {
                        stateAndEventConsumer.resetPollInterval()
                        processEvent(event, outputRecords, updatedStates)
                    }
                }
            } catch (ex: StateAndEventConsumer.RebalanceInProgressException) {
                log.warn(
                    "Abandoning processing of events(keys: ${events.joinToString { it.key.toString() }}, " +
                            "size: ${events.size}) due to rebalance", ex
                )
                return true
            }

//        commitTimer.recordCallable {
//            producer.beginTransaction()
//            producer.sendRecords(outputRecords.toCordaProducerRecords())
//            if (deadLetterRecords.isNotEmpty()) {
//                producer.sendRecords(deadLetterRecords.map {
//                    CordaProducerRecord(
//                        getDLQTopic(eventTopic),
//                        UUID.randomUUID().toString(),
//                        it
//                    )
//                })
//                deadLetterRecords.clear()
//            }
//            producer.sendRecordOffsetsToTransaction(eventConsumer, events)
//            producer.commitTransaction()
//        }
            // yuk!!
            // Using Any type and cast later so not to expose the Kafka type.
            //  this needs to be wrapped.
            val meta = stateAndEventConsumer.eventConsumer.kafkaGroupMetadata()
            sendingChannel2.send(StateAndEventData2(outputRecords.toCordaProducerRecords(), events, meta))

            log.debug { "Processing events(keys: ${events.joinToString { it.key.toString() }}, size: ${events.size}) complete." }

            stateAndEventConsumer.updateInMemoryStatePostCommit(updatedStates, clock)
        return false
    }

    private fun processEvent(
        event: CordaConsumerRecord<K, E>,
        outputRecords: MutableList<Record<*, *>>,
        updatedStates: MutableMap<Int, MutableMap<K, S?>>
    ) {
        log.debug { "Processing event: $event" }
        val key = event.key
        val state = updatedStates[event.partition]?.get(event.key)
        val partitionId = event.partition
        val thisEventUpdates = getUpdatesForEvent(state, event)
        val updatedState = thisEventUpdates?.updatedState


        when {
            thisEventUpdates == null -> {
                log.warn(
                    "Sending state and event on key ${event.key} for topic ${event.topic} to dead letter queue. " +
                            "Processor failed to complete."
                )
                generateChunkKeyCleanupRecords(key, state, null, outputRecords)
                outputRecords.add(generateDeadLetterRecord(event, state))
                outputRecords.add(Record(stateTopic, key, null))
                updatedStates.computeIfAbsent(partitionId) { mutableMapOf() }[key] = null
            }

            thisEventUpdates.markForDLQ -> {
                log.warn(
                    "Sending state and event on key ${event.key} for topic ${event.topic} to dead letter queue. " +
                            "Processor marked event for the dead letter queue"
                )
                generateChunkKeyCleanupRecords(key, state, null, outputRecords)
                outputRecords.add(generateDeadLetterRecord(event, state))
                outputRecords.add(Record(stateTopic, key, null))
                updatedStates.computeIfAbsent(partitionId) { mutableMapOf() }[key] = null

                // In this case the processor may ask us to publish some output records regardless, so make sure these
                // are outputted.
                outputRecords.addAll(thisEventUpdates.responseEvents)
            }

            else -> {
                generateChunkKeyCleanupRecords(key, state, updatedState, outputRecords)
                outputRecords.addAll(thisEventUpdates.responseEvents)
                outputRecords.add(Record(stateTopic, key, updatedState))
                updatedStates.computeIfAbsent(partitionId) { mutableMapOf() }[key] = updatedState
                log.debug { "Completed event: $event" }
            }
        }
    }

    /**
     * If the new state requires old chunk keys to be cleared then generate cleanup records to set those ChunkKeys to null
     */
    private fun generateChunkKeyCleanupRecords(key: K, state: S?, updatedState: S?, outputRecords: MutableList<Record<*, *>>) {
        chunkSerializerService.getChunkKeysToClear(key, state, updatedState)?.let { chunkKeys ->
            chunkKeys.map { chunkKey ->
                outputRecords.add(Record(stateTopic, chunkKey, null))
            }
        }
    }

    private fun getUpdatesForEvent(state: S?, event: CordaConsumerRecord<K, E>): StateAndEventProcessor.Response<S>? {
        val future = stateAndEventConsumer.waitForFunctionToFinish(
            { processor.onNext(state, event.toRecord()) }, config.processorTimeout.toMillis(),
            "Failed to finish within the time limit for state: $state and event: $event"
        )
        @Suppress("unchecked_cast")
        return future.tryGetResult() as? StateAndEventProcessor.Response<S>
    }

    private fun generateDeadLetterRecord(event: CordaConsumerRecord<K, E>, state: S?): Record<*, *> {
        val keyBytes = ByteBuffer.wrap(cordaAvroSerializer.serialize(event.key))
        val stateBytes =
            if (state != null) ByteBuffer.wrap(cordaAvroSerializer.serialize(state)) else null
        val eventValue = event.value
        val eventBytes =
            if (eventValue != null) ByteBuffer.wrap(cordaAvroSerializer.serialize(eventValue)) else null
        return Record(
            getDLQTopic(eventTopic), event.key,
            StateAndEventDeadLetterRecord(clock.instant(), keyBytes, stateBytes, eventBytes)
        )
    }

    /**
     * Handle retries for event processing.
     * Reset [nullableEventConsumer] position and retry poll and process of eventRecords
     * Retry a max of [ResolvedSubscriptionConfig.processorRetries] times.
     * If [ResolvedSubscriptionConfig.processorRetries] is exceeded then throw a [CordaMessageAPIIntermittentException]
     */
    private fun handleProcessEventRetries(
        attempts: Int,
        ex: Exception
    ) {
        if (attempts <= config.processorRetries) {
            log.warn(
                "Failed to process record from topic $eventTopic, group ${config.group}, " +
                        "producerClientId ${config.clientId}. " +
                        "Retrying poll and process. Attempts: $attempts."
            )
            stateAndEventConsumer.resetEventOffsetPosition()
        } else {
            val message = "Failed to process records from topic $eventTopic, group ${config.group}, " +
                    "producerClientId ${config.clientId}. " +
                    "Attempts: $attempts. Max reties exceeded."
            log.warn(message, ex)
            throw CordaMessageAPIIntermittentException(message, ex)
        }
    }
}
