package net.corda.libs.interop.endpoints.v1

import net.corda.libs.interop.endpoints.v1.types.CreateInteropIdentityRequest
import java.util.*

class InteropIdentityManagerImpl : InteropManager {

    /**
     * Create an interop identity.
     */
    override fun createInteropIdentity(createInteropIdentityRequestDto: CreateInteropIdentityRequestDto): CreateInteropIdentityRequest {
        return CreateInteropIdentityRequest(
            createInteropIdentityRequestDto.x500Name,
            UUID.fromString(createInteropIdentityRequestDto.groupId)
        )
    }

    private var running = false

    override val isRunning: Boolean
        get() = running

    override fun start() {
        running = true
    }

    override fun stop() {
        running = false
    }
}