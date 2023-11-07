package net.corda.flow.session.impl

import net.corda.flow.session.SessionManager
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.publisher.Publisher
import net.corda.v5.base.types.MemberX500Name
import org.slf4j.LoggerFactory
import java.util.UUID

// For this prototype, assume all virtual nodes are local.
class SessionManagerImpl(
    private val stateManager: StateManager,
    private val publisher: Publisher
) : SessionManager {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosedClass)
        private const val INITIATED_SUFFIX = "-INITIATED"
    }

    override fun createSession(flowID: String, counterparty: MemberX500Name): String {
        val sessionID = UUID.randomUUID()

    }

    override fun sendMessage(sessionID: String, message: ByteArray) {
        TODO("Not yet implemented")
    }

    override fun receiveMessage(sessionID: String): ByteArray {
        TODO("Not yet implemented")
    }

    override fun deleteSession(sessionID: String) {
        TODO("Not yet implemented")
    }
}