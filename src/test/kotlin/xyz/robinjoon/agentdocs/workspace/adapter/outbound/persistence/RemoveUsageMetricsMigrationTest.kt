package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.support.persistence.MigratedH2Database
import java.sql.SQLException

// The rows are written as SQL against the V8 schema, because the adapters follow the latest schema, which later
// migrations change.
class RemoveUsageMetricsMigrationTest {
    private val database = MigratedH2Database(target = "8")

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `removing usage metrics drops the document opens and keeps documents, revisions, comments, and activity`() {
        v8Rows.forEach(database::execute)

        database.migrate(target = "9")

        assertThat(keptTables.associateWith(::count)).isEqualTo(keptTables.associateWith { 1 })
        assertThatThrownBy { database.execute("SELECT COUNT(*) FROM document_views") }.isInstanceOf(SQLException::class.java)
    }

    private fun count(table: String): Int =
        database.inTransaction {
            checkNotNull(
                TransactionManager.current().exec("SELECT COUNT(*) FROM $table") { rows ->
                    rows.next()
                    rows.getInt(1)
                },
            )
        }

    private companion object {
        const val SPACE = "aaaaaaaa-0000-4000-8000-000000000001"
        const val DOCUMENT = "aaaaaaaa-0000-4000-8000-000000000002"
        const val THREAD = "aaaaaaaa-0000-4000-8000-000000000003"
        const val COMMENT = "aaaaaaaa-0000-4000-8000-000000000004"
        const val USER = "11111111-1111-4111-8111-111111111111"
        const val AT = "TIMESTAMP WITH TIME ZONE '2026-10-18 09:00:00+00:00'"

        val keptTables = listOf("spaces", "documents", "document_revisions", "threads", "thread_comments", "activities")

        val v8Rows =
            listOf(
                "INSERT INTO spaces (id, slug, space_name, description, created_at) VALUES ('$SPACE', 'docs', 'Docs', '', $AT)",
                "INSERT INTO documents (id, space_id, doc_path, active_path, title, status, tags, body, search_text, revision, " +
                    "created_at, updated_at) VALUES " +
                    "('$DOCUMENT', '$SPACE', 'notes', 'notes', 'Notes', 'DRAFT', ' ', '', 'notes', 1, $AT, $AT)",
                "INSERT INTO document_revisions (document_id, revision, space_id, doc_path, title, status, tags, body, trashed, " +
                    "actor_kind, actor_user_id, actor_login, change_source, summary, changed_blocks, created_at, updated_at) VALUES " +
                    "('$DOCUMENT', 1, '$SPACE', 'notes', 'Notes', 'DRAFT', ' ', '', FALSE, " +
                    "'HUMAN', '$USER', 'robin', 'WEB', 'made', '', $AT, $AT)",
                "INSERT INTO threads (id, space_id, document_id, anchor_kind, anchored_revision, status, author_kind, created_at, " +
                    "updated_at) VALUES ('$THREAD', '$SPACE', '$DOCUMENT', 'DOCUMENT', 1, 'OPEN', 'HUMAN', $AT, $AT)",
                "INSERT INTO thread_comments (id, thread_id, comment_position, author_kind, author_user_id, author_login, body, " +
                    "created_at) VALUES ('$COMMENT', '$THREAD', 0, 'HUMAN', '$USER', 'robin', 'Shorter?', $AT)",
                "INSERT INTO activities (kind, actor_kind, actor_login, space_id, document_id, summary, occurred_at) VALUES " +
                    "('document.created', 'HUMAN', 'robin', '$SPACE', '$DOCUMENT', 'Notes', $AT)",
                "INSERT INTO document_views (document_id, viewer_login, viewed_on) VALUES " +
                    "('$DOCUMENT', 'robin', DATE '2026-10-18'), ('$DOCUMENT', 'robin', DATE '2026-10-19')",
            )
    }
}
