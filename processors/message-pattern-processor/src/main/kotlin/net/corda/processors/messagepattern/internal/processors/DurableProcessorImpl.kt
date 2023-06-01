package net.corda.processors.messagepattern.internal.processors

import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.processors.messagepattern.internal.ResolvedPatternConfig
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger

class DurableProcessorImpl(private val patternConfig: ResolvedPatternConfig): DurableProcessor<String, String> {

    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java)
    }


    private var counter = AtomicInteger()
    override fun onNext(events: List<Record<String, String>>): List<Record<*, *>> {
        counter.getAndAdd(events.size)
        val outputRecords = mutableListOf<Record<*, *>>()
        logger.debug("Read [${events.size}] events")
        logger.info("Total Read [$counter] events")

        if (patternConfig.processorDelay > 0 ) {
            logger.debug("Putting processor to sleep for [${patternConfig.processorDelay}] millis...")
            Thread.sleep(patternConfig.processorDelay.toLong())
            logger.debug("Processor awake after sleeping for [${patternConfig.processorDelay}] millis!")
        }

        if (patternConfig.outputRecordCount > 0 ) {
            val outputRecordCount = patternConfig.outputRecordCount * events.size
            logger.debug("Generating [${outputRecordCount}] output records...")
            outputRecords.addAll(generateOutputRecords(outputRecordCount, patternConfig.outputTopic, null, patternConfig.outputRecordSize))
            logger.debug("Generated [${outputRecordCount}] output records!")
        }

        return outputRecords
    }


    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<String>
        get() = String::class.java
}