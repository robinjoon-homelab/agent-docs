package xyz.robinjoon.agentdocs.access.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import xyz.robinjoon.agentdocs.access.application.port.output.OAuthClientRepository
import xyz.robinjoon.agentdocs.access.domain.client.ClientMetadata
import xyz.robinjoon.agentdocs.access.domain.client.OAuthClient
import java.time.ZoneOffset

class ExposedOAuthClientRepository : OAuthClientRepository {
    override fun find(clientId: String): OAuthClient? =
        OAuthClientsTable
            .selectAll()
            .where { OAuthClientsTable.clientId eq clientId }
            .singleOrNull()
            ?.toClient()

    override fun save(client: OAuthClient): OAuthClient {
        OAuthClientsTable.upsert {
            it[clientId] = client.clientId
            it[kind] = client.kind
            it[clientName] = client.metadata.name
            it[redirectUris] = client.metadata.redirectUris.joinToString("\n")
            it[registeredAt] = client.registeredAt.atOffset(ZoneOffset.UTC)
        }
        return requireNotNull(find(client.clientId))
    }

    private fun ResultRow.toClient(): OAuthClient =
        OAuthClient.restore(
            this[OAuthClientsTable.clientId],
            this[OAuthClientsTable.kind],
            ClientMetadata.of(
                this[OAuthClientsTable.clientName],
                this[OAuthClientsTable.redirectUris].split('\n').filter(String::isNotEmpty),
            ),
            this[OAuthClientsTable.registeredAt].toInstant(),
        )
}
