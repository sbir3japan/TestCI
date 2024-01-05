package net.corda.messagebus.api

/**
 * Topic/partition info for specific message queues on the message bus.
 */
data class CordaTopicPartition(val topic: String, val partition: Int) {
    companion object {
        fun toCordaTopicPartition(string: String): CordaTopicPartition {
             val values = string.split("/")
            return CordaTopicPartition(values.first(), values.last().toInt())
        }
    }

    override fun toString(): String {
        return "$topic/$partition"
    }
}




