package xyz.robinjoon.agentdocs.access.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

internal object UsersTable : Table("users") {
    val id = javaUUID("id")
    val githubId = long("github_id")
    val login = varchar("login", 39)
    val displayName = varchar("display_name", 255).nullable()
    val avatarUrl = varchar("avatar_url", 500).nullable()
    val lastSignedInAt = timestampWithTimeZone("last_signed_in_at")

    override val primaryKey = PrimaryKey(id)
}
