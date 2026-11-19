package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.support.persistence.MigratedH2Database
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentWrite
import xyz.robinjoon.agentdocs.workspace.application.model.OperationKey
import xyz.robinjoon.agentdocs.workspace.application.model.OperationRecord
import xyz.robinjoon.agentdocs.workspace.application.model.OperationResultState
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import java.util.UUID

class OperationResultStateMigrationTest {
    private val database = MigratedH2Database(target = "11")
    private val store = ExposedOperationStore()

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `existing response snapshots become available and older success evidence remains legacy`() {
        val document = PersistenceFixtures.document(SpaceId(UUID.randomUUID()), "old", emptyList())
        val legacy = OperationRecord("document.edit", document.id.value, 1)
        val available = legacy.copy(documentWrite = DocumentWrite(document, 1, emptyList(), false, false))
        insertOldRecord("legacy", legacy)
        insertOldRecord("available", available)

        database.migrate()

        assertThat(database.inTransaction { store.find(OperationKey("old", "legacy")) }).isEqualTo(legacy)
        assertThat(database.inTransaction { store.find(OperationKey("old", "available")) })
            .isEqualTo(available.copy(state = OperationResultState.AVAILABLE))
        assertThat(database.inTransaction { store.find(OperationKey("old", "unknown")) }).isNull()
    }

    private fun insertOldRecord(
        operation: String,
        record: OperationRecord,
    ) {
        val encoded = OperationSnapshotCodec.encode(record)?.replace("'", "''")
        val payload = if (encoded == null) "NULL" else "'$encoded'"
        database.execute(
            "INSERT INTO write_operations (writer, operation_id, kind, target_id, revision, payload, created_at) VALUES " +
                "('old', '$operation', '${record.kind}', '${record.target}', ${record.revision}, $payload, " +
                "TIMESTAMP WITH TIME ZONE '2026-10-18 09:00:00+00:00')",
        )
    }
}
