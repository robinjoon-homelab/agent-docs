package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

internal object PersonalDocumentsTable : Table("personal_documents") {
    val userId = javaUUID("user_id")
    val documentId = javaUUID("document_id")
    val viewedAt = timestampWithTimeZone("viewed_at").nullable()
    val favoriteAt = timestampWithTimeZone("favorite_at").nullable()
    override val primaryKey = PrimaryKey(userId, documentId)
}
