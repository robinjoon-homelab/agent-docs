package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.thread.Anchor
import xyz.robinjoon.agentdocs.workspace.domain.thread.Comment
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentEdit
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentHistory
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentId
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentThread
import xyz.robinjoon.agentdocs.workspace.domain.thread.Mentions
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadLocation
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadProgress
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadTimeline
import java.util.UUID

// How a thread maps to its row and its child rows. Children are rewritten whole on every write; a thread holds a
// handful of comments, so this stays cheap and keeps the mapping in one place.
internal object ThreadRows {
    fun write(
        statement: UpdateBuilder<*>,
        thread: CommentThread,
    ) {
        val location = thread.location
        statement[ThreadsTable.spaceId] = location.spaceId.value
        statement[ThreadsTable.documentId] = location.documentId.value
        writeAnchor(statement, location.anchor)
        statement[ThreadsTable.anchoredRevision] = location.anchoredRevision
        statement[ThreadsTable.status] = thread.status
        statement[ThreadsTable.authorKind] = thread.author.kind
        statement[ThreadsTable.updatedAt] =
            thread.progress.timeline.updatedAt
                .utc()
        statement[ThreadsTable.resolvedAt] =
            thread.progress.timeline.resolvedAt
                ?.utc()
        ThreadsTable.resolver.write(statement, thread.progress.timeline.resolvedBy)
    }

    fun writeChildren(thread: CommentThread) {
        deleteChildren(listOf(thread.id.value))
        thread.comments.forEachIndexed { position, comment -> insertComment(thread.id, position, comment) }
        ThreadRevisionsTable.batchInsert(thread.progress.linkedRevisions, shouldReturnGeneratedValues = false) { revision ->
            this[ThreadRevisionsTable.threadId] = thread.id.value
            this[ThreadRevisionsTable.revision] = revision
        }
    }

    fun deleteThreads(ids: List<UUID>) {
        if (ids.isEmpty()) return
        deleteChildren(ids)
        ThreadsTable.deleteWhere { id inList ids }
    }

    fun thread(row: ResultRow): CommentThread {
        val id = ThreadId(row[ThreadsTable.id])
        val location =
            ThreadLocation(
                SpaceId(row[ThreadsTable.spaceId]),
                DocumentId(row[ThreadsTable.documentId]),
                anchor(row),
                row[ThreadsTable.anchoredRevision],
            )
        val timeline =
            ThreadTimeline(
                row[ThreadsTable.createdAt].toInstant(),
                row[ThreadsTable.updatedAt].toInstant(),
                row[ThreadsTable.resolvedAt]?.toInstant(),
                ThreadsTable.resolver.read(row),
            )
        val revisions =
            ThreadRevisionsTable
                .selectAll()
                .where { ThreadRevisionsTable.threadId eq id.value }
                .orderBy(ThreadRevisionsTable.revision to SortOrder.ASC)
                .map { it[ThreadRevisionsTable.revision] }
        return CommentThread.restore(id, location, ThreadProgress.restore(row[ThreadsTable.status], revisions, timeline), comments(id))
    }

    private fun comments(id: ThreadId): List<Comment> =
        ThreadCommentsTable
            .selectAll()
            .where { ThreadCommentsTable.threadId eq id.value }
            .orderBy(ThreadCommentsTable.position to SortOrder.ASC)
            .map { row ->
                val commentId = row[ThreadCommentsTable.id]
                val history =
                    CommentHistory.of(
                        row[ThreadCommentsTable.createdAt].toInstant(),
                        edits(commentId),
                        row[ThreadCommentsTable.deletedAt]?.toInstant(),
                    )
                Comment(CommentId(commentId), ThreadCommentsTable.author.read(row), row[ThreadCommentsTable.body], history)
            }

    private fun edits(comment: UUID): List<CommentEdit> =
        CommentEditsTable
            .selectAll()
            .where { CommentEditsTable.commentId eq comment }
            .orderBy(CommentEditsTable.position to SortOrder.ASC)
            .map { CommentEdit(it[CommentEditsTable.previousBody], it[CommentEditsTable.editedAt].toInstant()) }

    private fun insertComment(
        thread: ThreadId,
        position: Int,
        comment: Comment,
    ) {
        ThreadCommentsTable.insert {
            it[id] = comment.id.value
            it[threadId] = thread.value
            it[this.position] = position
            author.write(it, comment.author)
            it[body] = comment.body
            it[createdAt] = comment.history.createdAt.utc()
            it[deletedAt] = comment.history.deletedAt?.utc()
        }
        CommentEditsTable.batchInsert(comment.history.edits.withIndex(), shouldReturnGeneratedValues = false) { (index, edit) ->
            this[CommentEditsTable.commentId] = comment.id.value
            this[CommentEditsTable.position] = index
            this[CommentEditsTable.previousBody] = edit.previousBody
            this[CommentEditsTable.editedAt] = edit.editedAt.utc()
        }
        val mentioned = if (comment.isDeleted) emptySet() else Mentions.of(comment.body)
        CommentMentionsTable.batchInsert(mentioned, shouldReturnGeneratedValues = false) { login ->
            this[CommentMentionsTable.commentId] = comment.id.value
            this[CommentMentionsTable.login] = login
        }
    }

    private fun deleteChildren(threads: List<UUID>) {
        val comments =
            ThreadCommentsTable
                .select(ThreadCommentsTable.id)
                .where {
                    ThreadCommentsTable.threadId inList threads
                }.map { it[ThreadCommentsTable.id] }
        if (comments.isNotEmpty()) {
            CommentEditsTable.deleteWhere { commentId inList comments }
            CommentMentionsTable.deleteWhere { commentId inList comments }
            ThreadCommentsTable.deleteWhere { id inList comments }
        }
        ThreadRevisionsTable.deleteWhere { threadId inList threads }
    }

    private fun writeAnchor(
        statement: UpdateBuilder<*>,
        anchor: Anchor,
    ) {
        val cell = anchor as? Anchor.Cell
        val text = anchor as? Anchor.Text
        statement[ThreadsTable.anchorKind] =
            when (anchor) {
                is Anchor.Text -> TEXT
                is Anchor.Cell -> CELL
                is Anchor.Whole -> BLOCK
                Anchor.Document -> DOCUMENT
            }
        statement[ThreadsTable.anchorBlock] = (text?.blockId ?: cell?.blockId ?: (anchor as? Anchor.Whole)?.blockId)?.value
        statement[ThreadsTable.anchorRow] = cell?.rowId?.value
        statement[ThreadsTable.anchorColumn] = cell?.columnId?.value
        statement[ThreadsTable.anchorStart] = text?.start
        statement[ThreadsTable.anchorEnd] = text?.end
        statement[ThreadsTable.anchorQuote] = text?.quote ?: cell?.quote
    }

    private fun anchor(row: ResultRow): Anchor {
        val block = row[ThreadsTable.anchorBlock]?.let(::BlockId)
        return when (row[ThreadsTable.anchorKind]) {
            TEXT -> {
                Anchor.Text(
                    requireNotNull(block),
                    requireNotNull(row[ThreadsTable.anchorStart]),
                    requireNotNull(row[ThreadsTable.anchorEnd]),
                    quoteOf(row),
                )
            }

            CELL -> {
                Anchor.Cell(
                    requireNotNull(block),
                    BlockId(requireNotNull(row[ThreadsTable.anchorRow])),
                    BlockId(requireNotNull(row[ThreadsTable.anchorColumn])),
                    quoteOf(row),
                )
            }

            BLOCK -> {
                Anchor.Whole(requireNotNull(block))
            }

            else -> {
                Anchor.Document
            }
        }
    }

    private fun quoteOf(row: ResultRow): String = row[ThreadsTable.anchorQuote].orEmpty()

    private const val TEXT = "TEXT"
    private const val CELL = "CELL"
    private const val BLOCK = "BLOCK"
    private const val DOCUMENT = "DOCUMENT"
}
