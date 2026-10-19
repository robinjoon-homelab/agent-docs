package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

internal object ThreadRevisionsTable : Table("thread_revisions") {
    val threadId = javaUUID("thread_id")
    val revision = integer("revision")

    override val primaryKey = PrimaryKey(threadId, revision)
}
