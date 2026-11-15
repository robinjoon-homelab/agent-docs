package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.support.persistence.MigratedH2Database
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import java.util.UUID

class VersionRevisionMigrationTest {
    private val database = MigratedH2Database(target = "9")

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `version migration preserves deployed content revisions and comment anchor numbers`() {
        oldRows.forEach(database::execute)

        database.migrate(target = "10")

        val id = DocumentId(UUID.fromString(DOCUMENT))
        val current = database.inTransaction { ExposedDocumentRepository().findById(id) }
        val snapshot = database.inTransaction { ExposedRevisionCatalog().find(id, 3) }
        val anchored =
            database.inTransaction {
                TransactionManager.current().exec("SELECT anchored_revision FROM threads WHERE id = '$THREAD'") { rows ->
                    rows.next()
                    rows.getInt(1)
                }
            }
        assertThat(current?.version).isEqualTo(3)
        assertThat(current?.revision).isEqualTo(3)
        assertThat(snapshot?.version).isEqualTo(3)
        assertThat(snapshot?.content?.blocks).isEqualTo(current?.blocks)
        assertThat(snapshot?.change?.summary).isEqualTo("kept")
        assertThat(anchored).isEqualTo(3)
        assertThat(database.inTransaction { ExposedDocumentRepository().pending(id) }).isNull()
    }

    private companion object {
        const val SPACE = "aaaaaaaa-0000-4000-8000-000000000001"
        const val DOCUMENT = "aaaaaaaa-0000-4000-8000-000000000002"
        const val THREAD = "aaaaaaaa-0000-4000-8000-000000000003"
        const val USER = "11111111-1111-4111-8111-111111111111"
        const val AT = "TIMESTAMP WITH TIME ZONE '2026-10-18 09:00:00+00:00'"
        const val BODY = "<!-- b:para01 h:00000000 -->\nPreserved text"
        val oldRows =
            listOf(
                "INSERT INTO spaces (id, slug, space_name, description, created_at) VALUES ('$SPACE', 'docs', 'Docs', '', $AT)",
                "INSERT INTO documents (id, space_id, doc_path, active_path, title, status, tags, body, search_text, revision, " +
                    "created_at, updated_at) VALUES " +
                    "('$DOCUMENT', '$SPACE', 'notes', 'notes', 'Notes', 'DRAFT', ' ', '$BODY', 'notes', 3, $AT, $AT)",
                "INSERT INTO document_revisions (document_id, revision, space_id, doc_path, title, status, tags, body, trashed, " +
                    "actor_kind, actor_user_id, actor_login, change_source, summary, changed_blocks, created_at, updated_at) VALUES " +
                    "('$DOCUMENT', 3, '$SPACE', 'notes', 'Notes', 'DRAFT', ' ', '$BODY', FALSE, " +
                    "'HUMAN', '$USER', 'robin', 'WEB', 'kept', 'para01', $AT, $AT)",
                "INSERT INTO threads (id, space_id, document_id, anchor_kind, anchored_revision, status, author_kind, created_at, " +
                    "updated_at) VALUES ('$THREAD', '$SPACE', '$DOCUMENT', 'DOCUMENT', 3, 'OPEN', 'HUMAN', $AT, $AT)",
            )
    }
}
