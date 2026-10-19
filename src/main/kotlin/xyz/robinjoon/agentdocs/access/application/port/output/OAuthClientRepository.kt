package xyz.robinjoon.agentdocs.access.application.port.output

import xyz.robinjoon.agentdocs.access.domain.client.OAuthClient

interface OAuthClientRepository {
    fun find(clientId: String): OAuthClient?

    // Inserts or replaces the client with the same id.
    fun save(client: OAuthClient): OAuthClient
}
