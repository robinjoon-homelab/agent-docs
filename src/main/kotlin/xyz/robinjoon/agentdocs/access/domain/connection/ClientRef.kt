package xyz.robinjoon.agentdocs.access.domain.connection

import xyz.robinjoon.agentdocs.access.domain.InvalidAccessException

// The OAuth client a connection was issued to and how history shows it. host is the client id's host for a metadata
// document client, such as claude.ai, which identifies the client better than the name it reports about itself.
data class ClientRef(
    val clientId: String?,
    val name: String,
    val host: String?,
) {
    init {
        if (name.isBlank() || name.length > MAX_NAME_LENGTH) {
            throw InvalidAccessException("a connection name is 1 to $MAX_NAME_LENGTH characters")
        }
    }

    companion object {
        const val MAX_NAME_LENGTH = 100
    }
}
