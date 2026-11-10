package xyz.robinjoon.agentdocs.access.application.port.input

import xyz.robinjoon.agentdocs.access.application.model.Caller

interface CallerUseCase {
    // The caller behind a bearer token, or null when the token is unknown, expired, or its connection was revoked.
    fun callerFor(token: String): Caller?

    // The connection an upload token names, as it is now, or null when the token is malformed or the connection has
    // ended. This does not prove the token genuine: the workspace context does that before this runs and again when it
    // takes the file, so this caller must reach nothing but the upload endpoint.
    fun callerForUpload(token: String): Caller?
}
