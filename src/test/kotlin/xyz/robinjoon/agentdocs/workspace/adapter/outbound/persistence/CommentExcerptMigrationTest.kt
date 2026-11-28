package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.adapter.outbound.persistence.ExposedActivityLog
import xyz.robinjoon.agentdocs.application.model.ActivityQuery
import xyz.robinjoon.agentdocs.support.persistence.MigratedH2Database
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.at
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.person
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.inbox.InboxItem
import xyz.robinjoon.agentdocs.workspace.domain.inbox.InboxItemId
import xyz.robinjoon.agentdocs.workspace.domain.inbox.InboxKind
import xyz.robinjoon.agentdocs.workspace.domain.inbox.Notice
import xyz.robinjoon.agentdocs.workspace.domain.inbox.NoticeTarget
import xyz.robinjoon.agentdocs.workspace.domain.thread.Anchor
import xyz.robinjoon.agentdocs.workspace.domain.thread.Comment
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentId
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentThread
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadLocation
import java.util.UUID

class CommentExcerptMigrationTest {
    private val database = MigratedH2Database(target = "24")
    private val inbox = ExposedInboxRepository()
    private val log = ExposedActivityLog()
    private val threads = ExposedThreadRepository()

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `migration scrubs deleted inbox and activity excerpts including legacy links while keeping rows and metadata`() {
        val fixture = TransferPersistenceFixtures(database)
        val root = legacyThread(legacyDocument(fixture))
        val opened = root.comments.single()
        val deletedReply = Comment.write(CommentId(UUID.randomUUID()), person, "Old private reply", at.plusSeconds(10))
        val liveReply = Comment.write(CommentId(UUID.randomUUID()), person, "Live reply", at.plusSeconds(20))
        val withReplies = root.reply(deletedReply, deletedReply.history.createdAt).reply(liveReply, liveReply.history.createdAt)
        val deleted =
            withReplies
                .deleteComment(
                    opened.id,
                    person,
                    at.plusSeconds(30),
                ).deleteComment(deletedReply.id, person, at.plusSeconds(30))
        insertLegacyThread(deleted)
        insertActivity(root, opened, "thread.opened", linked = false)
        insertActivity(root, deletedReply, "comment.added", linked = true)
        insertActivity(root, liveReply, "comment.added", linked = true)
        val items = withReplies.comments.map { item(root, it) }
        database.inTransaction { inbox.addAll(items) }

        database.migrate()

        val entries = database.inTransaction { log.find(ActivityQuery(null, null, 20, oldestFirst = true)) }.map { it.record }
        assertThat(entries.map { it.summary }).containsExactly("삭제된 댓글", "삭제된 댓글", "Live reply")
        assertThat(entries.map { it.createdComment?.id }).containsExactly(null, deletedReply.id.value, liveReply.id.value)
        assertThat(entries.map { it.subject.threadId }).containsOnly(root.id.value)
        assertThat(
            entries.map { it.at },
        ).containsExactly(opened.history.createdAt, deletedReply.history.createdAt, liveReply.history.createdAt)
        val notices = database.inTransaction { items.map { requireNotNull(inbox.findById(it.id)) } }
        assertThat(notices.map { it.notice.excerpt }).containsExactly("", "", "Live reply")
        assertThat(notices.map { it.notice.target }).containsExactlyElementsOf(items.map { it.notice.target })
        assertThat(notices.map { it.delivery }).containsExactlyElementsOf(items.map { it.delivery })
        assertThat(database.inTransaction { threads.findById(root.id) }).isEqualTo(deleted)
    }

    private fun legacyThread(doc: Document): CommentThread {
        val opened = Comment.write(CommentId(UUID.randomUUID()), person, "Old private opening", at)
        return CommentThread.open(ThreadId(UUID.randomUUID()), ThreadLocation(doc.spaceId, doc.id, Anchor.Document, 1), opened, at)
    }

    private fun insertLegacyThread(thread: CommentThread) {
        val location = thread.location
        val timeline = thread.progress.timeline
        // The seed represents V24, so it must not write columns added by a later migration.
        database.execute(
            "INSERT INTO threads (id, space_id, document_id, anchor_kind, anchored_revision, " +
                "status, author_kind, created_at, updated_at) " +
                "VALUES ('${thread.id.value}', '${location.spaceId}', '${location.documentId}', 'DOCUMENT', 1, 'OPEN', 'HUMAN', " +
                "TIMESTAMP WITH TIME ZONE '${timeline.createdAt}', TIMESTAMP WITH TIME ZONE '${timeline.updatedAt}')",
        )
        thread.comments.forEachIndexed { i, c ->
            val deleted = c.history.deletedAt?.let { "TIMESTAMP WITH TIME ZONE '$it'" } ?: "NULL"
            val body = c.body.replace("'", "''")
            database.execute(
                "INSERT INTO thread_comments (id, thread_id, comment_position, author_kind, author_user_id, author_login, " +
                    "body, created_at, deleted_at) VALUES ('${c.id.value}', '${thread.id.value}', $i, 'HUMAN', '${person.userId}', " +
                    "'${person.login}', '$body', TIMESTAMP WITH TIME ZONE '${c.history.createdAt}', $deleted)",
            )
        }
    }

    @Test
    fun `a retained exact delete key redacts its linked activity after purge`() {
        val deleted = UUID.randomUUID()
        val other = UUID.randomUUID()
        database.execute(
            "INSERT INTO write_operations (writer, operation_id, kind, target_id, created_at, result_state) VALUES " +
                "('person', 'delete-once', 'comment.delete', '$deleted', TIMESTAMP WITH TIME ZONE '$at', 'REMOVED')",
        )
        database.execute(
            "INSERT INTO activities (kind, actor_kind, summary, occurred_at, created_comment_id) VALUES " +
                "('comment.added', 'HUMAN', 'Private', TIMESTAMP WITH TIME ZONE '$at', '$deleted'), " +
                "('comment.added', 'HUMAN', 'Other', TIMESTAMP WITH TIME ZONE '$at', '$other')",
        )

        database.migrate()

        val entries = database.inTransaction { log.find(ActivityQuery(null, null, 20, oldestFirst = true)) }.map { it.record }
        assertThat(entries.map { it.summary }).containsExactly("삭제된 댓글", "Other")
        assertThat(entries.map { it.createdComment?.id }).containsExactly(deleted, other)
    }

    // Seed the V24 schema directly; current document adapters require later columns.
    private fun legacyDocument(fixture: TransferPersistenceFixtures): xyz.robinjoon.agentdocs.workspace.domain.document.Document {
        val document = PersistenceFixtures.document(fixture.source.id, "doc", emptyList())
        database.execute(
            "INSERT INTO documents (id, space_id, doc_path, active_path, title, status, tags, body, search_text, " +
                "revision, content_version, created_at, updated_at) VALUES " +
                "('${document.id}', '${document.spaceId}', 'doc', 'doc', 'Doc', 'DRAFT', '', '', '', 1, 1, " +
                "TIMESTAMP WITH TIME ZONE '$at', TIMESTAMP WITH TIME ZONE '$at')",
        )
        return document
    }

    private fun item(
        thread: CommentThread,
        comment: Comment,
    ): InboxItem {
        val target = NoticeTarget(thread.location.spaceId, thread.location.documentId, thread.id, comment.id)
        val notice = Notice.of(InboxKind.REPLY, target, person, comment.body)
        return InboxItem.deliver(InboxItemId(UUID.randomUUID()), "reader", notice, comment.history.createdAt)
    }

    private fun insertActivity(
        thread: CommentThread,
        comment: Comment,
        kind: String,
        linked: Boolean,
    ) {
        val commentId = if (linked) "'${comment.id.value}'" else "NULL"
        database.execute(
            "INSERT INTO activities (kind, actor_kind, actor_user_id, actor_login, space_id, document_id, thread_id, " +
                "summary, occurred_at, created_comment_id) " +
                "VALUES ('$kind', 'HUMAN', '${person.userId}', '${person.login}', '${thread.location.spaceId.value}', " +
                "'${thread.location.documentId.value}', '${thread.id.value}', '${comment.body}', " +
                "TIMESTAMP WITH TIME ZONE '${comment.history.createdAt}', $commentId)",
        )
    }
}
