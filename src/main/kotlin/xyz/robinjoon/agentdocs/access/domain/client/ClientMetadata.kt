package xyz.robinjoon.agentdocs.access.domain.client

import xyz.robinjoon.agentdocs.access.domain.InvalidAccessException
import java.util.Collections

@ConsistentCopyVisibility
data class ClientMetadata private constructor(
    val name: String,
    val redirectUris: List<String>,
) {
    init {
        if (name.isBlank() || name.length > MAX_NAME_LENGTH) {
            throw InvalidAccessException("a client name is 1 to $MAX_NAME_LENGTH characters")
        }
        if (redirectUris.isEmpty() || redirectUris.size > MAX_REDIRECTS) {
            throw InvalidAccessException("a client has 1 to $MAX_REDIRECTS redirect URIs")
        }
    }

    companion object {
        const val MAX_NAME_LENGTH = 100
        const val MAX_REDIRECTS = 10

        fun of(
            name: String,
            redirectUris: List<String>,
        ): ClientMetadata =
            ClientMetadata(name.trim().take(MAX_NAME_LENGTH), Collections.unmodifiableList(redirectUris.map(RedirectUris::requireValid)))
    }
}
