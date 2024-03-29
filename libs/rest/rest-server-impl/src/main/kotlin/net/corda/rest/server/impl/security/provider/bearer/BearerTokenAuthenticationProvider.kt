package net.corda.rest.server.impl.security.provider.bearer

import net.corda.rest.authorization.AuthorizingSubject
import net.corda.rest.server.impl.security.provider.AuthenticationProvider
import net.corda.rest.server.impl.security.provider.credentials.AuthenticationCredentials
import net.corda.rest.server.impl.security.provider.credentials.tokens.BearerTokenAuthenticationCredentials

internal abstract class BearerTokenAuthenticationProvider : AuthenticationProvider {
    override fun supports(credential: AuthenticationCredentials): Boolean {
        return credential is BearerTokenAuthenticationCredentials
    }

    override fun authenticate(credential: AuthenticationCredentials): AuthorizingSubject {
        if (credential !is BearerTokenAuthenticationCredentials) {
            throw IllegalArgumentException("Provider only supports bearer tokens.")
        }

        return doAuthenticate(credential)
    }

    protected abstract fun doAuthenticate(credential: BearerTokenAuthenticationCredentials): AuthorizingSubject
}
