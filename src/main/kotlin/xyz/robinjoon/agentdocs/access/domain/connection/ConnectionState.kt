package xyz.robinjoon.agentdocs.access.domain.connection

data class ConnectionState(
    val term: ConnectionTerm,
    val credentials: Credentials,
)
