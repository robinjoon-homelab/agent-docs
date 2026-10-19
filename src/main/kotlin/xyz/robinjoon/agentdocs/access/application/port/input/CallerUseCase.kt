package xyz.robinjoon.agentdocs.access.application.port.input

import xyz.robinjoon.agentdocs.access.application.model.Caller

interface CallerUseCase {
    // The caller behind a bearer token, or null when the token is unknown, expired, or its connection was revoked.
    fun callerFor(token: String): Caller?
}
