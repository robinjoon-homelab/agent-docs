package xyz.robinjoon.agentdocs.access.domain.client

import xyz.robinjoon.agentdocs.domain.AggregateRoot
import java.net.URI
import java.time.Instant

// An MCP client that may ask for a connection. Its id is how Claude Code, Codex, and other clients are told apart;
// for a metadata document client, the host of that id is what the approval screen shows.
@AggregateRoot
@ConsistentCopyVisibility
data class OAuthClient private constructor(
    val clientId: String,
    val kind: ClientKind,
    val metadata: ClientMetadata,
    val registeredAt: Instant,
) {
    val host: String? get() = if (kind == ClientKind.METADATA_DOCUMENT) URI(clientId).host else null

    fun allowsRedirect(uri: String): Boolean = metadata.redirectUris.any { RedirectUris.matches(it, uri) }

    // A metadata document can change; the stored copy follows it on the next authorization.
    fun refreshed(metadata: ClientMetadata): OAuthClient = copy(metadata = metadata)

    companion object {
        fun register(
            clientId: String,
            kind: ClientKind,
            metadata: ClientMetadata,
            at: Instant,
        ): OAuthClient = OAuthClient(clientId, kind, metadata, at)

        fun restore(
            clientId: String,
            kind: ClientKind,
            metadata: ClientMetadata,
            registeredAt: Instant,
        ): OAuthClient = OAuthClient(clientId, kind, metadata, registeredAt)
    }
}
