package net.corda.processors.messagepattern.internal.processors

import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.processors.messagepattern.internal.ResolvedPatternConfig
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

class RPCResponderProcessorImpl(private val resolvedPatternConfig: ResolvedPatternConfig): RPCResponderProcessor<String, String> {

    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java)
    }

    private var counter = 0
    override fun onNext(request: String, respFuture: CompletableFuture<String>) {
        counter++
        logger.info("Processed [$counter] records.")
        logger.debug { "Received request [$request], completing future with response of size [${resolvedPatternConfig.outputRecordSize}]..." }
        respFuture.complete(generateValue(resolvedPatternConfig.outputRecordSize))
        logger.debug { "Completed future with response of size [${resolvedPatternConfig.outputRecordSize}]!" }
    }
}