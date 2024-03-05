package net.corda.messaging.mediator.metrics

import net.corda.messaging.constants.MetricsConstants
import net.corda.metrics.CordaMetrics

class EventMediatorMetrics(
    mediatorName: String,
) {
    val processorTimer = CordaMetrics.Metric.Messaging.MessageProcessorTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternType, MetricsConstants.EVENT_MEDIATOR_TYPE)
        .withTag(CordaMetrics.Tag.MessagePatternClientId, mediatorName)
        .withTag(CordaMetrics.Tag.OperationName, MetricsConstants.BATCH_PROCESS_OPERATION)
        .build()

    val pollTimer = CordaMetrics.Metric.Messaging.ConsumerPollTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternClientId, mediatorName)
        .build()

    val commitTimer = CordaMetrics.Metric.Messaging.MessageCommitTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternType, MetricsConstants.EVENT_MEDIATOR_TYPE)
        .withTag(CordaMetrics.Tag.MessagePatternClientId, mediatorName)
        .build()

    val consumerProcessorFailureCounter = CordaMetrics.Metric.Messaging.ConsumerProcessorFailureCount.builder()
        .withTag(CordaMetrics.Tag.MessagePatternClientId, mediatorName)
        .build()

    val eventProcessorFailureCounter = CordaMetrics.Metric.Messaging.EventProcessorFailureCount.builder()
        .withTag(CordaMetrics.Tag.MessagePatternClientId, mediatorName)
        .build()

    /**
     * This metric records how long an asynchronous event was waiting for a prior event in the same batch
     * to complete before it could begin processing.
     */
    val asyncEventWaitTimer = CordaMetrics.Metric.Messaging.AsyncMessageProcessingTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternClientId, mediatorName)
        .withTag(CordaMetrics.Tag.OperationName, MetricsConstants.ASYNC_WAIT_TIME)
        .build()
}