package net.corda.processors.messagepattern.internal

/**
 * Resolved config used to start pattern(s)
 */
data class ResolvedLoadDriverConfig(
    //delay between batches
    val processorDelay: Int,
    //delay before first batch
    val startupDelay: Int,
    //batch size to send between delays
    val outputRecordCount: Int,
    //topic to send to
    val outputTopic: String,
    //Size if the string payload
    val outputRecordSize: RecordSizeType,
    //Number of records to send
    val count: String,
    //Type of sender
    val type: SenderType,
    //consumer group
    val group: String
)


enum class SenderType {
    PUBLISHER, RPC
}