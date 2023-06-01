package net.corda.processors.messagepattern

import net.corda.libs.configuration.SmartConfig

/** The load driver processor for a `MessagePattern`. */
interface LoadDriverProcessor {
    fun start(bootConfig: SmartConfig)

    fun stop()
}