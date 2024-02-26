package net.corda.libs.statemanager.impl.metrics

import net.corda.metrics.CordaMetrics

class MetricsRecorderImpl(private val stateType: String) : MetricsRecorder {

    override fun <T> recordProcessingTime(operationType: MetricsRecorder.OperationType, block: () -> T): T {
        return CordaMetrics.Metric.StateManger.ExecutionTime.builder()
            .withTag(CordaMetrics.Tag.OperationName, operationType.toString())
            .withTag(CordaMetrics.Tag.StateType, stateType)
            .build()
            .recordCallable {
                block()
            }!!
    }

    override fun recordBatchSize(operationType: MetricsRecorder.OperationType, size: Double) {
        CordaMetrics.Metric.StateManger.BatchSize.builder()
            .withTag(CordaMetrics.Tag.OperationName, operationType.toString())
            .withTag(CordaMetrics.Tag.StateType, stateType)
            .build()
            .record(size)
    }

    override fun recordFailureCount(operationType: MetricsRecorder.OperationType, count: Int) {
        CordaMetrics.Metric.StateManger.FailureCount.builder()
            .withTag(CordaMetrics.Tag.OperationName, operationType.toString())
            .withTag(CordaMetrics.Tag.StateType, stateType)
            .build()
            .increment(count.toDouble())
    }
}
