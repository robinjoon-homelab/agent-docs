package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.select
import xyz.robinjoon.agentdocs.workspace.application.model.GuideCandidate
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceGuideCatalog
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId

// Reads only the columns that say where a guide document is now, so listing spaces never loads document bodies.
class ExposedSpaceGuideCatalog : SpaceGuideCatalog {
    override fun find(ids: Set<DocumentId>): List<GuideCandidate> {
        if (ids.isEmpty()) return emptyList()
        return DocumentsTable
            .select(DocumentsTable.id, DocumentsTable.spaceId, DocumentsTable.title, DocumentsTable.path, DocumentsTable.trashedAt)
            .where { DocumentsTable.id inList ids.map { it.value } }
            .map {
                GuideCandidate(
                    DocumentId(it[DocumentsTable.id]),
                    SpaceId(it[DocumentsTable.spaceId]),
                    it[DocumentsTable.title],
                    it[DocumentsTable.path],
                    it[DocumentsTable.trashedAt] != null,
                )
            }
    }
}
