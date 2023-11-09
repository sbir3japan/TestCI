package net.corda.flow.session

import net.corda.data.KeyValuePairList
import net.corda.virtualnode.HoldingIdentity

interface SessionManager {

    fun createSession(flowID: String, config: SessionConfig): String

    fun sendMessage(sessionID: String, message: ByteArray)

    fun receiveMessage(sessionID: String): ByteArray

    fun deleteSession(sessionID: String)

    data class SessionConfig(
        val cpiId: String,
        val party: HoldingIdentity,
        val counterparty: HoldingIdentity,
        val contextSessionProperties: KeyValuePairList,
        val requireClose: Boolean = true
    )
}