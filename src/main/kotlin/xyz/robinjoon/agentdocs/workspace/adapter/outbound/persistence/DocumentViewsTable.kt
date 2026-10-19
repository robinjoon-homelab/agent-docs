package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.date

internal object DocumentViewsTable : Table("document_views") {
    val documentId = javaUUID("document_id")
    val viewerLogin = varchar("viewer_login", 39)
    val viewedOn = date("viewed_on")

    override val primaryKey = PrimaryKey(documentId, viewerLogin, viewedOn)
}
