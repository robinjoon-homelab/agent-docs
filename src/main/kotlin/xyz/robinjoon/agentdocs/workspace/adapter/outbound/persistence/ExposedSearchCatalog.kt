package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.jdbc.select
import xyz.robinjoon.agentdocs.workspace.application.model.CommentMatch
import xyz.robinjoon.agentdocs.workspace.application.port.output.SearchCatalog
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId

// LIKE over lowercase text. search_text is stored lowercase already; titles and comment bodies are lowered here.
class ExposedSearchCatalog : SearchCatalog {
    override fun documents(
        spaces: Set<SpaceId>,
        term: String,
        limit: Int,
    ): List<DocumentId> =
        live(spaces) { DocumentsTable.searchText like LikePatterns.containing(term.lowercase()) }
            .orderBy(DocumentsTable.updatedAt to SortOrder.DESC)
            .limit(limit)
            .map { DocumentId(it[DocumentsTable.id]) }

    override fun titles(
        spaces: Set<SpaceId>,
        term: String,
        limit: Int,
    ): List<DocumentId> =
        live(spaces) { DocumentsTable.title.lowerCase() like LikePatterns.containing(term.lowercase()) }
            .orderBy(DocumentsTable.updatedAt to SortOrder.DESC)
            .limit(limit)
            .map { DocumentId(it[DocumentsTable.id]) }

    override fun comments(
        spaces: Set<SpaceId>,
        term: String,
        limit: Int,
    ): List<CommentMatch> {
        if (spaces.isEmpty()) return emptyList()
        return ThreadCommentsTable
            .join(ThreadsTable, JoinType.INNER, ThreadCommentsTable.threadId, ThreadsTable.id)
            .join(DocumentsTable, JoinType.INNER, ThreadsTable.documentId, DocumentsTable.id)
            .select(ThreadsTable.documentId, ThreadsTable.id, ThreadCommentsTable.body)
            .where {
                (ThreadsTable.spaceId inList spaces.map { it.value }) and DocumentsTable.trashedAt.isNull() and
                    ThreadCommentsTable.deletedAt.isNull() and
                    (ThreadCommentsTable.body.lowerCase() like LikePatterns.containing(term.lowercase()))
            }.orderBy(ThreadCommentsTable.createdAt to SortOrder.DESC)
            .limit(limit)
            .map { CommentMatch(DocumentId(it[ThreadsTable.documentId]), ThreadId(it[ThreadsTable.id]), it[ThreadCommentsTable.body]) }
    }

    private fun live(
        spaces: Set<SpaceId>,
        matches: () -> Op<Boolean>,
    ) = DocumentsTable
        .select(DocumentsTable.id)
        .where { (DocumentsTable.spaceId inList spaces.map { it.value }) and DocumentsTable.trashedAt.isNull() and matches() }
}
