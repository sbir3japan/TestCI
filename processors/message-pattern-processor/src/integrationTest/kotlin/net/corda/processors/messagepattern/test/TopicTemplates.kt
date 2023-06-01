package net.corda.processors.messagepattern.test

import net.corda.schema.Schemas.getStateAndEventDLQTopic
import net.corda.schema.Schemas.getStateAndEventStateTopic
import java.util.UUID

class TopicTemplates {
    companion object {
        const val DURABLE_TOPIC1 = "DurableTopic1"
        val DURABLE_TOPIC1_TEMPLATE = """topics = [ 
                    { 
                        topicName = "-P$DURABLE_TOPIC1" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    } 
                ]"""


        const val EVENT_TOPIC1 = "EventTopic1"
        private val EVENT_TOPIC1_DLQ = getStateAndEventDLQTopic(EVENT_TOPIC1)
        private val EVENT_TOPIC1_STATE = getStateAndEventStateTopic(EVENT_TOPIC1)
        val EVENT_TOPIC1_TEMPLATE = """topics = [ 
                    { 
                        topicName = "-P$EVENT_TOPIC1" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    }, 
                    { 
                        topicName = "-P$EVENT_TOPIC1_DLQ" 
                        numPartitions = 2 
                        replicationFactor = 3 
                    }, 
                    { 
                        topicName = "-P$EVENT_TOPIC1_STATE" 
                        numPartitions = 2 
                        replicationFactor = 3 
                        config { 
                            cleanup.policy=compact 
                        } 
                    } 
                ]"""


        const val RPC_TOPIC1 = "RPCTopic1"
        val RPC_TOPIC1_TEMPLATE = """topics = [
                    {
                        topicName = "-P$RPC_TOPIC1" 
                        numPartitions = 1
                        replicationFactor = 3
                        config {
                            cleanup.policy=compact
                        }
                    }
                ]"""

        private const val RPC_RESPONSE_TOPIC1 = "$RPC_TOPIC1.resp"
        val RPC_RESPONSE_TOPIC1_TEMPLATE = """topics = [
                    { 
                        topicName = "-P$RPC_RESPONSE_TOPIC1"
                        numPartitions = 1
                        replicationFactor = 3
                        config { 
                            cleanup.policy=compact
                        } 
                    } 
                ]"""

    }
}
