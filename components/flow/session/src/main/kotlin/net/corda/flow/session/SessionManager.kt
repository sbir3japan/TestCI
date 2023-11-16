package net.corda.flow.session

import net.corda.data.KeyValuePairList
import net.corda.virtualnode.HoldingIdentity

interface SessionManager : AutoCloseable {

    fun checkSessionExists(sessionID: String) : Boolean

    fun createSession(sessionID: String, config: SessionConfig)

    fun sendMessage(sessionID: String, message: ByteArray)

    fun receiveMessage(sessionID: String): ByteArray

    fun deleteSession(sessionID: String)

    fun getCounterpartyProperties(sessionID: String) : KeyValuePairList

    data class SessionConfig(
        val cpiId: String,
        val party: HoldingIdentity,
        val counterparty: HoldingIdentity,
        val contextSessionProperties: KeyValuePairList,
        val requireClose: Boolean = true
    )
}