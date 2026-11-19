package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import xyz.robinjoon.agentdocs.workspace.application.model.OperationKey
import xyz.robinjoon.agentdocs.workspace.application.model.OperationRecord
import xyz.robinjoon.agentdocs.workspace.application.model.OperationResultState
import xyz.robinjoon.agentdocs.workspace.application.port.output.OperationStore
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentId
import java.time.Instant
import java.util.UUID

class ExposedOperationStore : OperationStore {
    override fun findForWrite(key: OperationKey): OperationRecord? {
        // A fixed set of rows also serializes keys that have no result yet, without retaining failed attempts.
        val bucket = Math.floorMod(key.hashCode(), LOCK_BUCKETS)
        WriteOperationLocksTable
            .selectAll()
            .where { WriteOperationLocksTable.bucket eq bucket }
            .forUpdate()
            .single()
        return find(key)
    }

    internal fun find(key: OperationKey): OperationRecord? =
        WriteOperationsTable
            .selectAll()
            .where { (WriteOperationsTable.writer eq key.writer) and (WriteOperationsTable.operationId eq key.operationId) }
            .singleOrNull()
            ?.let(::recordOf)

    private fun recordOf(row: ResultRow): OperationRecord {
        val record =
            OperationRecord(
                row[WriteOperationsTable.kind],
                row[WriteOperationsTable.targetId],
                row[WriteOperationsTable.revision],
                state = row[WriteOperationsTable.resultState],
                purgedFrom = row[WriteOperationsTable.purgedFrom]?.let(::SpaceId),
            )
        val payload = row[WriteOperationsTable.payload] ?: return record
        return OperationSnapshotCodec.decode(record, payload)
    }

    override fun removeCommentResults(comment: CommentId) {
        removeResults { OperationSnapshotCodec.containsComment(it, comment) }
    }

    override fun removeDocumentResults(documents: Map<DocumentId, SpaceId>) {
        if (documents.isEmpty()) return
        val ids = documents.keys.map { it.value }.toSet()
        removeResults { OperationSnapshotCodec.containsDocument(it, ids) }
        documents.entries.groupBy({ it.value }, { it.key.value }).forEach { (space, targets) ->
            markPurged(space, targets)
        }
    }

    private fun markPurged(
        space: SpaceId,
        targets: List<UUID>,
    ) {
        WriteOperationsTable.update({
            (WriteOperationsTable.targetId inList targets) and
                (WriteOperationsTable.resultState eq OperationResultState.REMOVED) and
                ((WriteOperationsTable.kind like "document.%") or (WriteOperationsTable.kind eq "revision.name"))
        }) { it[purgedFrom] = space.value }
    }

    private fun removeResults(containsDeletedContent: (String) -> Boolean) {
        val affected =
            WriteOperationsTable
                .selectAll()
                .where { WriteOperationsTable.payload.isNotNull() }
                .filter { containsDeletedContent(requireNotNull(it[WriteOperationsTable.payload])) }
        affected.forEach { row ->
            WriteOperationsTable.update({
                (WriteOperationsTable.writer eq row[WriteOperationsTable.writer]) and
                    (WriteOperationsTable.operationId eq row[WriteOperationsTable.operationId])
            }) {
                it[payload] = null
                it[resultState] = OperationResultState.REMOVED
            }
        }
    }

    override fun save(
        key: OperationKey,
        record: OperationRecord,
        at: Instant,
    ) {
        val snapshot = OperationSnapshotCodec.encode(record)
        WriteOperationsTable.insert {
            it[writer] = key.writer
            it[operationId] = key.operationId
            it[kind] = record.kind
            it[targetId] = record.target
            it[revision] = record.revision
            it[payload] = snapshot
            it[resultState] = if (snapshot != null) OperationResultState.AVAILABLE else record.state
            it[createdAt] = at.utc()
        }
    }

    private companion object {
        const val LOCK_BUCKETS = 256
    }
}
