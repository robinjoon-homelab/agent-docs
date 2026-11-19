package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import xyz.robinjoon.agentdocs.workspace.application.model.OperationResultState

internal object WriteOperationsTable : Table("write_operations") {
    val writer = varchar("writer", 60)
    val operationId = varchar("operation_id", 100)
    val kind = varchar("kind", 40)
    val targetId = javaUUID("target_id")
    val revision = integer("revision").nullable()
    val payload = text("payload").nullable()
    val resultState = enumerationByName<OperationResultState>("result_state", 16)
    val purgedFrom = javaUUID("purged_from_space_id").nullable()
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(writer, operationId)
}

internal object WriteOperationLocksTable : Table("write_operation_locks") {
    val bucket = integer("bucket")

    override val primaryKey = PrimaryKey(bucket)
}
