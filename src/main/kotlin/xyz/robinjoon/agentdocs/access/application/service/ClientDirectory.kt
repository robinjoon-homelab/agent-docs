package xyz.robinjoon.agentdocs.access.application.service

import xyz.robinjoon.agentdocs.access.application.model.ClientRegistration
import xyz.robinjoon.agentdocs.access.application.model.OAuthException
import xyz.robinjoon.agentdocs.access.application.port.output.ClientMetadataSource
import xyz.robinjoon.agentdocs.access.application.port.output.OAuthClientRepository
import xyz.robinjoon.agentdocs.access.application.port.output.SecretSource
import xyz.robinjoon.agentdocs.access.domain.InvalidAccessException
import xyz.robinjoon.agentdocs.access.domain.client.ClientKind
import xyz.robinjoon.agentdocs.access.domain.client.ClientMetadata
import xyz.robinjoon.agentdocs.access.domain.client.OAuthClient
import java.time.Clock
import java.time.Instant

// Finds the client behind a client id. An https client id is a Client ID Metadata Document: the server fetches it on
// every authorization and trusts only a document whose client_id is that same URL. Any other id must have been
// registered dynamically. Only public clients, which authenticate with PKCE alone, are accepted.
class ClientDirectory(
    private val clients: OAuthClientRepository,
    private val documents: ClientMetadataSource,
    private val secrets: SecretSource,
    private val clock: Clock,
) {
    fun resolve(clientId: String): OAuthClient {
        if (!clientId.startsWith("https://")) {
            return clients.find(clientId)?.takeIf { it.kind == ClientKind.REGISTERED }
                ?: throw OAuthException("invalid_client", "unknown client_id")
        }
        requireDocumentUrl(clientId)
        val document = documents.fetch(clientId)
        if (document.clientId != clientId) throw OAuthException("invalid_client", "the metadata document names another client_id")
        requirePublic(document.tokenEndpointAuthMethod)
        val metadata = metadataOf(document.clientName ?: hostOf(clientId), document.redirectUris)
        val client =
            clients.find(clientId)?.refreshed(metadata)
                ?: OAuthClient.register(clientId, ClientKind.METADATA_DOCUMENT, metadata, Instant.now(clock))
        return clients.save(client)
    }

    fun register(registration: ClientRegistration): OAuthClient {
        requirePublic(registration.tokenEndpointAuthMethod)
        val unsupported = registration.grantTypes.orEmpty() - setOf("authorization_code", "refresh_token")
        if (unsupported.isNotEmpty()) {
            throw OAuthException(
                "invalid_client_metadata",
                "unsupported grant_types: ${unsupported.joinToString()}",
            )
        }
        val metadata = metadataOf(registration.clientName ?: "MCP client", registration.redirectUris)
        return clients.save(OAuthClient.register("adc_" + secrets.next(), ClientKind.REGISTERED, metadata, Instant.now(clock)))
    }

    private fun metadataOf(
        name: String,
        redirectUris: List<String>,
    ): ClientMetadata =
        try {
            ClientMetadata.of(name, redirectUris)
        } catch (exception: InvalidAccessException) {
            throw OAuthException("invalid_redirect_uri", exception.message.orEmpty(), cause = exception)
        }

    private fun requirePublic(method: String?) {
        if (method != null && method != "none") {
            throw OAuthException("invalid_client_metadata", "only public clients are supported: token_endpoint_auth_method must be none")
        }
    }

    // https with a path, and no fragment, credentials, or dot segments (draft-ietf-oauth-client-id-metadata-document).
    private fun requireDocumentUrl(clientId: String) {
        val afterScheme = clientId.removePrefix("https://")
        val path = afterScheme.substringAfter('/', "")
        val valid =
            path.isNotEmpty() && '#' !in clientId && '@' !in afterScheme.substringBefore('/') &&
                path.split('/').none { it == "." || it == ".." }
        if (!valid) throw OAuthException("invalid_client", "a client_id URL must be https with a path and no fragment or dot segments")
    }

    private fun hostOf(clientId: String): String = clientId.removePrefix("https://").substringBefore('/').substringBefore(':')
}
