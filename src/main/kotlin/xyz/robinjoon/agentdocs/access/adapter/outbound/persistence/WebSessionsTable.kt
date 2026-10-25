package xyz.robinjoon.agentdocs.access.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

internal object WebSessionsTable : Table("web_sessions") {
    val id = javaUUID("id")
    val userId = javaUUID("user_id")
    val tokenHash = varchar("token_hash", 64)
    val createdAt = timestampWithTimeZone("created_at")
    val lastUsedAt = timestampWithTimeZone("last_used_at")

    override val primaryKey = PrimaryKey(id)
}
