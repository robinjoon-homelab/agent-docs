package xyz.robinjoon.agentdocs.access.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import xyz.robinjoon.agentdocs.access.domain.client.ClientKind

internal object OAuthClientsTable : Table("oauth_clients") {
    val clientId = varchar("client_id", 500)
    val kind = enumerationByName("kind", 20, ClientKind::class)
    val clientName = varchar("client_name", 100)
    val redirectUris = text("redirect_uris")
    val registeredAt = timestampWithTimeZone("registered_at")

    override val primaryKey = PrimaryKey(clientId)
}
