package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.support.persistence.MigratedH2Database
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.at
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.document
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.revisionOf
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.space
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceStatistics

class ExposedSpaceStatisticsCatalogTest {
    private val database = MigratedH2Database()
    private val catalog = ExposedSpaceStatisticsCatalog()

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `documents outside the trash are counted per space with the latest change`() {
        val spaces = ExposedSpaceRepository()
        val documents = ExposedDocumentRepository()
        val docs = database.inTransaction { spaces.add(space("docs")) }
        val empty = database.inTransaction { spaces.add(space("empty")) }
        val kept = document(docs.id, "kept", emptyList())
        val gone = document(docs.id, "gone", emptyList())
        database.inTransaction { documents.add(kept, revisionOf(kept)) }
        database.inTransaction { documents.add(gone, revisionOf(gone)) }
        val trashed = requireNotNull(gone.trash(at.plusSeconds(5)))
        database.inTransaction { documents.update(trashed, revisionOf(trashed), amended = false) }

        assertThat(
            database.inTransaction { catalog.forSpaces(setOf(docs.id, empty.id)) },
        ).isEqualTo(mapOf(docs.id to SpaceStatistics(1, at)))
    }
}
