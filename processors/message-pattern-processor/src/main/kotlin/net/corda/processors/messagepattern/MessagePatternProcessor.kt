package net.corda.processors.messagepattern

import net.corda.libs.configuration.SmartConfig

/** The processor for a `MessagePattern`. */
interface MessagePatternProcessor {
    fun start(bootConfig: SmartConfig)

    fun stop()
}