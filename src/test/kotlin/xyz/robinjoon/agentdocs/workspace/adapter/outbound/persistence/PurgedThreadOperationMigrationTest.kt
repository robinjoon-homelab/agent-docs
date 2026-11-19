package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.support.persistence.MigratedH2Database
import xyz.robinjoon.agentdocs.workspace.application.model.OperationKey
import xyz.robinjoon.agentdocs.workspace.application.model.OperationRecord
import xyz.robinjoon.agentdocs.workspace.application.model.OperationResultState
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import java.util.UUID

class PurgedThreadOperationMigrationTest {
    private val database = MigratedH2Database(target = "29")
    private val store = ExposedOperationStore()

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `an existing removed result uses the documented purge space when the thread and comment are gone`() {
        val document = UUID.randomUUID()
        val thread = UUID.randomUUID()
        val comment = UUID.randomUUID()
        val source = UUID.randomUUID()
        val destination = SpaceId(UUID.randomUUID())
        val unknown = UUID.randomUUID()
        val timestamp = "TIMESTAMP WITH TIME ZONE '2026-11-01 00:00:00+00:00'"
        database.execute(
            "INSERT INTO activities (kind, actor_kind, space_id, document_id, thread_id, created_comment_id, summary, occurred_at) " +
                "VALUES " +
                "('comment.added', 'HUMAN', '$source', '$document', '$thread', '$comment', 'added', $timestamp), " +
                "('document.purged', 'SYSTEM', '${destination.value}', '$document', NULL, NULL, 'purged', $timestamp)",
        )
        database.execute(
            "INSERT INTO write_operations (writer, operation_id, kind, target_id, result_state, created_at) VALUES " +
                "('author', 'edit', 'comment.edit', '$comment', 'REMOVED', $timestamp), " +
                "('author', 'legacy', 'thread.reply', '$comment', 'LEGACY', $timestamp), " +
                "('author', 'suggest', 'thread.suggest', '$thread', 'REMOVED', $timestamp), " +
                "('author', 'unknown', 'thread.reply', '$unknown', 'REMOVED', $timestamp)",
        )

        database.migrate()

        assertThat(database.inTransaction { store.find(OperationKey("author", "edit")) })
            .isEqualTo(OperationRecord("comment.edit", comment, null, state = OperationResultState.REMOVED, purgedFrom = destination))
        assertThat(database.inTransaction { store.find(OperationKey("author", "suggest")) })
            .isEqualTo(OperationRecord("thread.suggest", thread, null, state = OperationResultState.REMOVED, purgedFrom = destination))
        assertThat(database.inTransaction { store.find(OperationKey("author", "legacy")) })
            .isEqualTo(OperationRecord("thread.reply", comment, null, state = OperationResultState.LEGACY, purgedFrom = destination))
        assertThat(database.inTransaction { store.find(OperationKey("author", "unknown")) })
            .isEqualTo(OperationRecord("thread.reply", unknown, null, state = OperationResultState.REMOVED))
    }
}
