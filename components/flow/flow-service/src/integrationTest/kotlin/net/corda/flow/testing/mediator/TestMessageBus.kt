package net.corda.flow.testing.mediator

import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messaging.api.mediator.MediatorMessage

interface TestMessageBus {
    fun <K, V> poll(topic: String, pollRecords: Int): List<CordaConsumerRecord<K, V>>

    fun send(topic: String, message: MediatorMessage<*>)
}