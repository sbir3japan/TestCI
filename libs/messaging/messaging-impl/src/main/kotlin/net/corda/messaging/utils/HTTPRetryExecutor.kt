package net.corda.messaging.utils

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class HTTPRetryExecutor {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        fun <T> withConfig(config: HTTPRetryConfig, block: () -> T): T {
            var currentDelay = config.initialDelay
            for (i in 0 until config.times - 1) {
                try {
                    log.info("HTTPRetryExecutor making attempt #${i + 1}.")
                    val result = block()
                    log.info("Operation successful after #${i + 1} attempt/s.")
                    return result
                } catch (e: Exception) {
                    if (config.retryOn.none { it.isInstance(e) }) {
                        log.warn("HTTPRetryExecutor caught a non-retryable exception")
                        throw e
                    }

                    log.warn("Attempt #${i + 1} failed due to ${e.message}. Retrying in $currentDelay ms...")
                    Thread.sleep(currentDelay)
                    currentDelay = (currentDelay * config.factor).toLong()
                }
            }

            log.warn("All retry attempts exhausted. Making the final call.")

            try {
                val result = block()
                log.info("Operation successful after #${config.times} attempt/s.")
                return result
            } catch (e: Exception) {
                log.error("Operation failed after ${config.times} attempt/s.")
                throw e
            }
        }
    }
}
