package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import xyz.robinjoon.agentdocs.workspace.application.model.ThreadCriteria
import xyz.robinjoon.agentdocs.workspace.application.port.output.ThreadRepository
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentId
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentThread
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId

class ExposedThreadRepository : ThreadRepository {
    override fun add(thread: CommentThread): CommentThread {
        ThreadsTable.insert {
            it[id] = thread.id.value
            it[createdAt] =
                thread.progress.timeline.createdAt
                    .utc()
            ThreadRows.write(it, thread)
        }
        ThreadRows.writeChildren(thread)
        return selectById(thread.id).single().let(ThreadRows::thread)
    }

    override fun update(thread: CommentThread): CommentThread {
        val updated = ThreadsTable.update({ ThreadsTable.id eq thread.id.value }) { ThreadRows.write(it, thread) }
        check(updated == 1) { "Thread to update does not exist: ${thread.id}" }
        ThreadRows.writeChildren(thread)
        return selectById(thread.id).single().let(ThreadRows::thread)
    }

    override fun findById(id: ThreadId): CommentThread? = selectById(id).singleOrNull()?.let(ThreadRows::thread)

    override fun findByIdForUpdate(id: ThreadId): CommentThread? = selectById(id).forUpdate().singleOrNull()?.let(ThreadRows::thread)

    override fun findByCommentForUpdate(id: CommentId): CommentThread? {
        val thread = ThreadCommentsTable.select(ThreadCommentsTable.threadId).where { ThreadCommentsTable.id eq id.value }.singleOrNull()
        return thread?.let { findByIdForUpdate(ThreadId(it[ThreadCommentsTable.threadId])) }
    }

    override fun findByDocument(document: DocumentId): List<CommentThread> =
        ThreadsTable
            .selectAll()
            .where { ThreadsTable.documentId eq document.value }
            .orderBy(ThreadsTable.createdAt to SortOrder.ASC)
            .map(ThreadRows::thread)

    override fun find(criteria: ThreadCriteria): List<CommentThread> {
        val query = criteria.query
        if (query.spaces?.isEmpty() == true || query.statuses?.isEmpty() == true) return emptyList()
        val select = ThreadsTable.selectAll()
        query.spaces?.let { spaces -> select.andWhere { ThreadsTable.spaceId inList spaces.map { it.value } } }
        query.document?.let { document -> select.andWhere { ThreadsTable.documentId eq document.value } }
        query.statuses?.let { statuses -> select.andWhere { ThreadsTable.status inList statuses } }
        query.authorKind?.let { kind -> select.andWhere { ThreadsTable.authorKind eq kind } }
        query.since?.let { since -> select.andWhere { ThreadsTable.updatedAt greater since.utc() } }
        query.mention?.let { login -> select.andWhere { ThreadsTable.id inSubQuery mentioning(login.lowercase()) } }
        return select.orderBy(ThreadsTable.updatedAt to SortOrder.DESC).limit(criteria.limit).map(ThreadRows::thread)
    }

    override fun linksByRevision(document: DocumentId): Map<Int, List<ThreadId>> =
        ThreadRevisionsTable
            .join(ThreadsTable, JoinType.INNER, ThreadRevisionsTable.threadId, ThreadsTable.id)
            .select(ThreadRevisionsTable.revision, ThreadRevisionsTable.threadId)
            .where { ThreadsTable.documentId eq document.value }
            .groupBy({ it[ThreadRevisionsTable.revision] }, { ThreadId(it[ThreadRevisionsTable.threadId]) })

    private fun mentioning(login: String): Query =
        ThreadCommentsTable
            .join(CommentMentionsTable, JoinType.INNER, ThreadCommentsTable.id, CommentMentionsTable.commentId)
            .select(ThreadCommentsTable.threadId)
            .where { CommentMentionsTable.login eq login }

    private fun selectById(id: ThreadId): Query = ThreadsTable.selectAll().where { ThreadsTable.id eq id.value }
}
