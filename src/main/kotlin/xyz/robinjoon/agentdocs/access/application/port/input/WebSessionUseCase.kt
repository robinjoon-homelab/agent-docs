package xyz.robinjoon.agentdocs.access.application.port.input

import xyz.robinjoon.agentdocs.access.application.model.SessionCaller
import java.util.UUID

interface WebSessionUseCase {
    // Starts a sign-in for a person GitHub just vouched for and returns the raw token for the browser's cookie.
    fun start(userId: UUID): String

    // The person behind a browser's token, or null when the token is unknown, went unused too long, or the account may
    // no longer sign in.
    fun callerFor(token: String): SessionCaller?

    fun end(token: String)
}
