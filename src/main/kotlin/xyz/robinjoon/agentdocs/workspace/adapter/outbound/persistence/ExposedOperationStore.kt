package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import xyz.robinjoon.agentdocs.workspace.application.model.OperationKey
import xyz.robinjoon.agentdocs.workspace.application.model.OperationRecord
import xyz.robinjoon.agentdocs.workspace.application.port.output.OperationStore
import java.time.Instant

class ExposedOperationStore : OperationStore {
    override fun find(key: OperationKey): OperationRecord? =
        WriteOperationsTable
            .selectAll()
            .where { (WriteOperationsTable.writer eq key.writer) and (WriteOperationsTable.operationId eq key.operationId) }
            .singleOrNull()
            ?.let { OperationRecord(it[WriteOperationsTable.kind], it[WriteOperationsTable.targetId], it[WriteOperationsTable.revision]) }

    // Two concurrent attempts with one id both pass find; the second insert then fails on the key and its write rolls
    // back, so the operation still applies once.
    override fun save(
        key: OperationKey,
        record: OperationRecord,
        at: Instant,
    ) {
        WriteOperationsTable.insert {
            it[writer] = key.writer
            it[operationId] = key.operationId
            it[kind] = record.kind
            it[targetId] = record.target
            it[revision] = record.revision
            it[createdAt] = at.utc()
        }
    }
}
