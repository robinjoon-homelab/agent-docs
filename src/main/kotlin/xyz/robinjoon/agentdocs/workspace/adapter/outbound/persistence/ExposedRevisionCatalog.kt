package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import xyz.robinjoon.agentdocs.workspace.application.model.BlockMark
import xyz.robinjoon.agentdocs.workspace.application.model.RevisionFilter
import xyz.robinjoon.agentdocs.workspace.application.model.RevisionSummary
import xyz.robinjoon.agentdocs.workspace.application.port.output.RevisionCatalog
import xyz.robinjoon.agentdocs.workspace.domain.Actor
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.history.Revision

class ExposedRevisionCatalog : RevisionCatalog {
    override fun find(
        document: DocumentId,
        number: Int,
    ): Revision? =
        DocumentRevisionsTable
            .selectAll()
            .where { (DocumentRevisionsTable.documentId eq document.value) and (DocumentRevisionsTable.revision eq number) }
            .singleOrNull()
            ?.let(RevisionRows::revision)

    // The body is not read for the list.
    override fun list(
        document: DocumentId,
        filter: RevisionFilter,
    ): List<RevisionSummary> {
        val columns = DocumentRevisionsTable.columns - DocumentRevisionsTable.body
        val query = DocumentRevisionsTable.select(columns).where { DocumentRevisionsTable.documentId eq document.value }
        filter.actorKind?.let { kind -> query.andWhere { DocumentRevisionsTable.actor.kind eq kind } }
        filter.from?.let { from -> query.andWhere { DocumentRevisionsTable.createdAt greaterEq from.utc() } }
        filter.to?.let { to -> query.andWhere { DocumentRevisionsTable.createdAt lessEq to.utc() } }
        return query
            .orderBy(DocumentRevisionsTable.revision to SortOrder.DESC)
            .map { RevisionSummary(it[DocumentRevisionsTable.revision], it[DocumentRevisionsTable.title], RevisionRows.change(it)) }
    }

    // Agent revisions carry the person who connected the agent, so they name that person as well.
    override fun authorOf(login: String): Actor? =
        DocumentRevisionsTable
            .select(DocumentRevisionsTable.actor.userId)
            .where { DocumentRevisionsTable.actor.login eq login }
            .orderBy(DocumentRevisionsTable.updatedAt to SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.let { Actor.human(it[DocumentRevisionsTable.actor.userId], login) }

    override fun marks(document: DocumentId): List<BlockMark> =
        DocumentBlockMarksTable
            .selectAll()
            .where { DocumentBlockMarksTable.documentId eq document.value }
            .map {
                BlockMark(
                    BlockId(it[DocumentBlockMarksTable.blockId]),
                    DocumentBlockMarksTable.actor.read(it),
                    it[DocumentBlockMarksTable.revision],
                    it[DocumentBlockMarksTable.changedAt].toInstant(),
                )
            }
}
