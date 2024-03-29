package net.corda.flow.external.events.responses.exceptions

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Used to indicate that a CPK is not allowed. This can happen if CPK is workflow CPK and is being loaded into sandbox
 * that only allows contract CPKs.
 */
class NotAllowedCpkException(message: String, cause: Throwable? = null) : CordaRuntimeException(message, cause)
