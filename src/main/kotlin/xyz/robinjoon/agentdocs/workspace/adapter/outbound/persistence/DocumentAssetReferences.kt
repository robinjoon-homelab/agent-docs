package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.select
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.document.AssetReferences
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.MarkdownAssetReferences

internal object DocumentAssetReferences {
    fun read(ids: Set<DocumentId>?): Map<DocumentId, Set<AssetId>> {
        val markdown =
            (commentSources(ids) + editSources(ids) + suggestionSources(ids)).map { (document, body) ->
                document to MarkdownAssetReferences.of(body)
            }
        return (documentSources(ids) + markdown).groupBy({ it.first }, { it.second }).mapValues { (_, sets) -> sets.flatten().toSet() }
    }

    // Bodies are read with their own document type, so flow image elements count like ordinary image blocks.
    private fun documentSources(ids: Set<DocumentId>?): List<Pair<DocumentId, Set<AssetId>>> {
        val current = DocumentsTable.select(DocumentsTable.id, DocumentsTable.body, DocumentsTable.type)
        val history =
            DocumentRevisionsTable
                .select(
                    DocumentRevisionsTable.documentId,
                    DocumentRevisionsTable.body,
                    DocumentRevisionsTable.type,
                ).where { DocumentRevisionsTable.pending eq false }
        if (ids != null) {
            current.andWhere { DocumentsTable.id inList ids.map { it.value } }
            history.andWhere { DocumentRevisionsTable.documentId inList ids.map { it.value } }
        }
        return current.map { DocumentId(it[DocumentsTable.id]) to references(it[DocumentsTable.body], it[DocumentsTable.type]) } +
            history.map {
                DocumentId(it[DocumentRevisionsTable.documentId]) to
                    references(it[DocumentRevisionsTable.body], it[DocumentRevisionsTable.type])
            }
    }

    private fun references(
        body: String,
        type: String,
    ): Set<AssetId> = AssetReferences.of(DocumentRows.blocksOf(body, type))

    private fun commentSources(ids: Set<DocumentId>?): List<Pair<DocumentId, String>> {
        val query =
            ThreadCommentsTable
                .join(ThreadsTable, JoinType.INNER, ThreadCommentsTable.threadId, ThreadsTable.id)
                .select(ThreadsTable.documentId, ThreadCommentsTable.body)
                .where { ThreadCommentsTable.deletedAt.isNull() }
        if (ids != null) query.andWhere { ThreadsTable.documentId inList ids.map { it.value } }
        return query.map { DocumentId(it[ThreadsTable.documentId]) to it[ThreadCommentsTable.body] }
    }

    private fun editSources(ids: Set<DocumentId>?): List<Pair<DocumentId, String>> {
        val query =
            CommentEditsTable
                .join(ThreadCommentsTable, JoinType.INNER, CommentEditsTable.commentId, ThreadCommentsTable.id)
                .join(ThreadsTable, JoinType.INNER, ThreadCommentsTable.threadId, ThreadsTable.id)
                .select(ThreadsTable.documentId, CommentEditsTable.previousBody)
                .where { ThreadCommentsTable.deletedAt.isNull() }
        if (ids != null) query.andWhere { ThreadsTable.documentId inList ids.map { it.value } }
        return query.map { DocumentId(it[ThreadsTable.documentId]) to it[CommentEditsTable.previousBody] }
    }

    private fun suggestionSources(ids: Set<DocumentId>?): List<Pair<DocumentId, String>> {
        val query = ThreadsTable.select(ThreadsTable.documentId, ThreadsTable.collaboration)
        if (ids != null) query.andWhere { ThreadsTable.documentId inList ids.map { it.value } }
        return query.flatMap { row ->
            val document = DocumentId(row[ThreadsTable.documentId])
            val collaboration = ThreadCollaborationCodec.deserialize(row[ThreadsTable.collaboration])
            collaboration.suggestions.flatMap { listOf(document to it.basis.before, document to it.basis.after) }
        }
    }
}
