package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.support.persistence.MigratedH2Database
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.at
import xyz.robinjoon.agentdocs.workspace.application.model.OperationKey
import xyz.robinjoon.agentdocs.workspace.application.model.OperationRecord
import java.util.UUID

class ExposedOperationStoreTest {
    private val database = MigratedH2Database()
    private val store = ExposedOperationStore()

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `an operation id is remembered per writer and cannot be stored twice`() {
        val record = OperationRecord("document.edit", UUID.randomUUID(), 3)
        database.inTransaction { store.save(OperationKey("writer-a", "op-1"), record, at) }

        assertThat(database.inTransaction { store.find(OperationKey("writer-a", "op-1")) }).isEqualTo(record)
        assertThat(database.inTransaction { store.find(OperationKey("writer-b", "op-1")) }).isNull()
        assertThatThrownBy { database.inTransaction { store.save(OperationKey("writer-a", "op-1"), record, at) } }
            .isInstanceOf(ExposedSQLException::class.java)
    }
}
