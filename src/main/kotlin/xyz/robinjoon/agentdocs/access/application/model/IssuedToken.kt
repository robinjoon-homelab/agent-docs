package xyz.robinjoon.agentdocs.access.application.model

import xyz.robinjoon.agentdocs.access.domain.connection.Connection

// The only moment the raw token exists outside the client: it is returned once and stored as a hash.
class IssuedToken(
    val connection: Connection,
    val token: String,
)
