package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentEventSnapshot
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentEventCatalog
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import java.security.MessageDigest

class ExposedDocumentEventCatalog : DocumentEventCatalog {
    override fun snapshot(id: DocumentId): DocumentEventSnapshot? {
        val row =
            DocumentsTable
                .select(
                    DocumentsTable.spaceId,
                    DocumentsTable.version,
                    DocumentsTable.revision,
                    DocumentsTable.trashedAt,
                ).where { DocumentsTable.id eq id.value }
                .singleOrNull() ?: return null
        return DocumentEventSnapshot(
            SpaceId(row[DocumentsTable.spaceId]),
            row[DocumentsTable.version],
            row[DocumentsTable.revision],
            row[DocumentsTable.trashedAt] != null,
            threadFingerprint(id),
        )
    }

    private fun threadFingerprint(id: DocumentId): String {
        val states =
            ThreadsTable
                .select(
                    ThreadsTable.id,
                    ThreadsTable.updatedAt,
                    ThreadsTable.status,
                    ThreadsTable.collaboration,
                ).where { ThreadsTable.documentId eq id.value }
                .map {
                    "${it[ThreadsTable.id]}:${it[ThreadsTable.updatedAt]}:${it[ThreadsTable.status]}:${it[ThreadsTable.collaboration]}"
                }.sorted()
                .joinToString("\n")
        return MessageDigest.getInstance("SHA-256").digest(states.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
