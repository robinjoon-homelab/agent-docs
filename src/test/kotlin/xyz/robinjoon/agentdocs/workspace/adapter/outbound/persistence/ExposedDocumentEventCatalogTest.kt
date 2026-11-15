package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.support.persistence.MigratedH2Database
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.document
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.revisionOf
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.space
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import java.util.UUID

class ExposedDocumentEventCatalogTest {
    private val database = MigratedH2Database()
    private val catalog = ExposedDocumentEventCatalog()

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `projection observes committed versions without loading content or creating revisions`() {
        val space = database.inTransaction { ExposedSpaceRepository().add(space("events")) }
        val document = document(space.id, "page", emptyList())
        database.inTransaction { ExposedDocumentRepository().add(document, revisionOf(document)) }
        val initial = database.inTransaction { requireNotNull(catalog.snapshot(document.id)) }
        database.inTransaction {
            DocumentsTable.update({ DocumentsTable.id eq document.id.value }) { it[DocumentsTable.version] = initial.version + 1 }
        }
        val changed = database.inTransaction { requireNotNull(catalog.snapshot(document.id)) }
        assertThat(changed.version).isEqualTo(initial.version + 1)
        assertThat(changed.revision).isEqualTo(initial.revision)
        assertThat(changed.threads).isEqualTo(initial.threads)
        assertThat(changed.spaceId).isEqualTo(space.id)
        assertThat(database.inTransaction { catalog.snapshot(DocumentId(UUID.randomUUID())) }).isNull()
    }
}
