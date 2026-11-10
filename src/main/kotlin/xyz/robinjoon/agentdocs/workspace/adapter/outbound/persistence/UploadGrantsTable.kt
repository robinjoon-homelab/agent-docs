package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

internal object UploadGrantsTable : Table("upload_grants") {
    val id = javaUUID("id")
    val spaceId = javaUUID("space_id")
    val issuer = ActorColumns(this, "issuer")
    val alt = text("alt")
    val secretHash = varchar("secret_hash", 64)
    val expiresAt = timestampWithTimeZone("expires_at")
    val usedAt = timestampWithTimeZone("used_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
