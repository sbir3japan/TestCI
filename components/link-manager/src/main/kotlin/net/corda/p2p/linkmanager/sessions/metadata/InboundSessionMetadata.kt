package net.corda.p2p.linkmanager.sessions.metadata

import net.corda.libs.statemanager.api.Metadata
import net.corda.utilities.time.Clock
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import java.time.Duration
import java.time.Instant

internal enum class InboundSessionStatus {
    SentResponderHello,
    SentResponderHandshake,
}

/**
 * [InboundSessionMetadata] represents the metadata stored in the State Manager for a session.
 *
 * @param lastSendTimestamp The last time a session negotiation message was sent.
 * @param expiry When the Session Expires and should be rotated.
 * @param status Where we are in the Session negotiation process.
 */
internal data class InboundSessionMetadata(
    val source: HoldingIdentity,
    val destination: HoldingIdentity,
    val lastSendTimestamp: Instant,
    val status: InboundSessionStatus,
    val expiry: Instant,
) {
    companion object {
        private const val SOURCE_VNODE = "sourceVnode"
        private const val DEST_VNODE = "destinationVnode"
        private const val GROUP_ID_KEY = "groupId"
        private const val LAST_SEND_TIMESTAMP = "lastSendTimestamp"
        private const val STATUS = "status"
        private const val EXPIRY = "expiry"
        private val SESSION_EXPIRY_PERIOD: Duration = Duration.ofDays(7)
        private val MESSAGE_EXPIRY_PERIOD: Duration = Duration.ofSeconds(2L)

        private fun String.statusFromString(): InboundSessionStatus {
            return InboundSessionStatus.values().first { it.toString() == this }
        }

        fun Metadata.from(): InboundSessionMetadata {
            return InboundSessionMetadata(
                HoldingIdentity(MemberX500Name.parse(this[SOURCE_VNODE].toString()), this[GROUP_ID_KEY].toString()),
                HoldingIdentity(MemberX500Name.parse(this[DEST_VNODE].toString()), this[GROUP_ID_KEY].toString()),
                Instant.ofEpochMilli(this[LAST_SEND_TIMESTAMP] as Long),
                this[STATUS].toString().statusFromString(),
                Instant.ofEpochMilli(this[EXPIRY] as Long),
            )
        }
    }

    fun lastSendExpired(clock: Clock): Boolean {
        return clock.instant() > lastSendTimestamp + MESSAGE_EXPIRY_PERIOD
    }

    fun sessionExpired(clock: Clock): Boolean {
        return clock.instant() > expiry + SESSION_EXPIRY_PERIOD
    }

    fun toMetadata(): Metadata {
        return Metadata(
            mapOf(
                SOURCE_VNODE to this.source.x500Name.toString(),
                DEST_VNODE to this.destination.x500Name.toString(),
                GROUP_ID_KEY to this.source.groupId,
                LAST_SEND_TIMESTAMP to this.lastSendTimestamp.toEpochMilli(),
                STATUS to this.status.toString(),
                EXPIRY to this.expiry.toEpochMilli(),
            ),
        )
    }
}
