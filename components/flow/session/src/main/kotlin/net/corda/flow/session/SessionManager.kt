package net.corda.flow.session

import net.corda.v5.base.types.MemberX500Name

interface SessionManager {

    fun createSession(flowID: String, counterparty: MemberX500Name): String

    fun sendMessage(sessionID: String, message: ByteArray)

    fun receiveMessage(sessionID: String): ByteArray

    fun deleteSession(sessionID: String)
}