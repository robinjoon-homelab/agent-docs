package xyz.robinjoon.agentdocs.access.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionKind
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionScope

internal object ConnectionsTable : Table("connections") {
    val id = javaUUID("id")
    val userId = javaUUID("user_id")
    val kind = enumerationByName("kind", 10, ConnectionKind::class)
    val clientId = varchar("client_id", 500).nullable()
    val clientName = varchar("client_name", 100)
    val clientHost = varchar("client_host", 255).nullable()
    val scope = enumerationByName("grant_scope", 10, ConnectionScope::class)
    val createdAt = timestampWithTimeZone("created_at")
    val expiresAt = timestampWithTimeZone("expires_at")
    val revokedAt = timestampWithTimeZone("revoked_at").nullable()
    val lastUsedAt = timestampWithTimeZone("last_used_at").nullable()
    val accessHash = varchar("access_hash", 64)
    val accessExpiresAt = timestampWithTimeZone("access_expires_at")
    val refreshHash = varchar("refresh_hash", 64).nullable()

    override val primaryKey = PrimaryKey(id)
}
