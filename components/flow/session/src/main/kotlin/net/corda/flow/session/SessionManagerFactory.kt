package net.corda.flow.session

import net.corda.libs.configuration.SmartConfig

interface SessionManagerFactory {

    fun create(stateManagerConfig: SmartConfig, messagingConfig: SmartConfig) : SessionManager
}