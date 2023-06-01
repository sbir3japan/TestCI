package net.corda.processors.messagepattern.internal.processors

import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.processors.messagepattern.internal.RecordSizeType
import net.corda.processors.messagepattern.internal.ResolvedPatternConfig
import net.corda.utilities.debug
import org.slf4j.LoggerFactory

class StateAndEventProcessorImpl (private val patternConfig: ResolvedPatternConfig): StateAndEventProcessor<String, String, String> {

    private val iterationCountMap = mutableMapOf<String, Int>()
    private var counter = 0

    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java)
    }

    override fun onNext(state: String?, event: Record<String, String>): StateAndEventProcessor.Response<String> {
        counter++
        logger.info("Currently active states [${iterationCountMap.size}], counter [$counter]" )
        val outputRecords = mutableListOf<Record<*, *>>()
        val key = event.key
        val iterationCount = iterationCountMap[key] ?: 1
        val newState = if (iterationCount < patternConfig.stateAndEventIterations) {
            outputRecords.add(generateOutputRecord(key, event.topic, patternConfig.outputRecordSize))
            logger.debug { "Generating output record for [$key]" }
            iterationCountMap[key] = iterationCount+1
            event.value
        } else {
            logger.debug { "Cleaning up state for [$key]" }
            iterationCountMap.remove(key)
            null
        }

        if (patternConfig.outputRecordCount > 0 ) {
            logger.debug { "Generating [${patternConfig.outputRecordCount}] additional output records..." }
            outputRecords.addAll(generateOutputRecords(patternConfig.outputRecordCount, patternConfig.outputTopic, null, patternConfig.outputRecordSize))
            logger.debug { "Generated [${patternConfig.outputRecordCount}] additional output records!" }
        }

        return StateAndEventProcessor.Response(newState, outputRecords)
    }

    override val keyClass: Class<String>
        get() = String::class.java
    override val stateValueClass: Class<String>
        get() = String::class.java
    override val eventValueClass: Class<String>
        get() = String::class.java
}