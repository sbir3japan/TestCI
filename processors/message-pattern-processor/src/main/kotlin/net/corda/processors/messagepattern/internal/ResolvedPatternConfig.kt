package net.corda.processors.messagepattern.internal

/**
 * Resolved config used to start pattern(s)
 *
 * important ones
 * - type
 * - outputRecordSize
 * - outputRecordCount
 * - count
 */
data class ResolvedPatternConfig(
    val group: String,
    val topic: String,
    val count: Int,
    val type: PatternType,
    val processorDelay: Int,
    val outputRecordCount: Int,
    val outputTopic: String,
    val outputRecordSize: RecordSizeType,
    val stateAndEventIterations: Int = 1,
    val startupDelay: Int = 0,
)

enum class PatternType {
    DURABLE, RPC, STATEANDEVENT
}

enum class RecordSizeType {
    SMALL, MEDIUM, CHECKPOINT, CHUNKED
}