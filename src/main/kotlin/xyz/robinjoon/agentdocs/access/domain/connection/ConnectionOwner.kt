package xyz.robinjoon.agentdocs.access.domain.connection

import xyz.robinjoon.agentdocs.access.domain.user.UserId

// The person a connection acts for and what kind of client holds it. Everything the connection writes is recorded as
// that client on behalf of this person.
data class ConnectionOwner(
    val userId: UserId,
    val kind: ConnectionKind,
    val client: ClientRef,
)
