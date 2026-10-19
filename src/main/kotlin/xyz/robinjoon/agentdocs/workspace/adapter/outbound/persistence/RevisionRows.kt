package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.update
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentHeader
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentProperties
import xyz.robinjoon.agentdocs.workspace.domain.document.history.ChangeOrigin
import xyz.robinjoon.agentdocs.workspace.domain.document.history.ChangePeriod
import xyz.robinjoon.agentdocs.workspace.domain.document.history.Revision
import xyz.robinjoon.agentdocs.workspace.domain.document.history.RevisionChange
import xyz.robinjoon.agentdocs.workspace.domain.document.history.RevisionContent

// How a revision maps to its row. Changed block ids are stored space-separated.
internal object RevisionRows {
    fun insert(revision: Revision) {
        DocumentRevisionsTable.insert {
            it[documentId] = revision.documentId.value
            it[this.revision] = revision.number
            it[createdAt] =
                revision.change.period.createdAt
                    .utc()
            write(it, revision)
        }
    }

    // Only the latest revision is ever replaced, while its author keeps typing.
    fun replace(revision: Revision) {
        val updated =
            DocumentRevisionsTable.update({
                (DocumentRevisionsTable.documentId eq revision.documentId.value) and (DocumentRevisionsTable.revision eq revision.number)
            }) { write(it, revision) }
        check(updated == 1) { "Revision to replace does not exist: ${revision.documentId} rev ${revision.number}" }
    }

    fun revision(row: ResultRow): Revision {
        val header =
            DocumentHeader(
                SpaceId(row[DocumentRevisionsTable.spaceId]),
                DocumentPath(row[DocumentRevisionsTable.path]),
                row[DocumentRevisionsTable.title],
                DocumentProperties.of(
                    DocumentRows.tagsFrom(row[DocumentRevisionsTable.tags]),
                    row[DocumentRevisionsTable.status],
                    row[DocumentRevisionsTable.assignee],
                ),
            )
        val content =
            RevisionContent.of(
                header,
                DocumentRows.blocksOf(row[DocumentRevisionsTable.body]),
                row[DocumentRevisionsTable.trashed],
            )
        return Revision(DocumentId(row[DocumentRevisionsTable.documentId]), row[DocumentRevisionsTable.revision], content, change(row))
    }

    fun change(row: ResultRow): RevisionChange =
        RevisionChange.restore(
            ChangeOrigin(DocumentRevisionsTable.actor.read(row), row[DocumentRevisionsTable.changeSource]),
            row[DocumentRevisionsTable.summary],
            row[DocumentRevisionsTable.changedBlocks].split(' ').filter(String::isNotEmpty).map(::BlockId),
            ChangePeriod(row[DocumentRevisionsTable.createdAt].toInstant(), row[DocumentRevisionsTable.updatedAt].toInstant()),
        )

    private fun write(
        statement: UpdateBuilder<*>,
        revision: Revision,
    ) {
        val header = revision.content.header
        statement[DocumentRevisionsTable.spaceId] = header.spaceId.value
        statement[DocumentRevisionsTable.path] = header.path.value
        statement[DocumentRevisionsTable.title] = header.title
        statement[DocumentRevisionsTable.status] = header.properties.status
        statement[DocumentRevisionsTable.tags] = DocumentRows.tagsOf(header.properties.tags)
        statement[DocumentRevisionsTable.assignee] = header.properties.assignee
        statement[DocumentRevisionsTable.body] = DocumentRows.bodyOf(revision.content.blocks)
        statement[DocumentRevisionsTable.trashed] = revision.content.trashed
        DocumentRevisionsTable.actor.write(statement, revision.change.origin.actor)
        statement[DocumentRevisionsTable.changeSource] = revision.change.origin.source
        statement[DocumentRevisionsTable.summary] = revision.change.summary
        statement[DocumentRevisionsTable.changedBlocks] = revision.change.changedBlocks.joinToString(" ") { it.value }
        statement[DocumentRevisionsTable.updatedAt] =
            revision.change.period.updatedAt
                .utc()
    }
}
