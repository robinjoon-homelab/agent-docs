package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.support.persistence.MigratedH2Database
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.at
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.document
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.revisionOf
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.space
import xyz.robinjoon.agentdocs.workspace.application.model.GuideCandidate
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import java.util.UUID

class ExposedSpaceGuideCatalogTest {
    private val database = MigratedH2Database()
    private val catalog = ExposedSpaceGuideCatalog()

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `a guide document is read with its space, title, path, and whether it is in the trash`() {
        val documents = ExposedDocumentRepository()
        val docs = database.inTransaction { ExposedSpaceRepository().add(space("docs")) }
        val guide = document(docs.id, "guide", emptyList())
        val old = document(docs.id, "old-guide", emptyList())
        database.inTransaction { documents.add(guide, revisionOf(guide)) }
        database.inTransaction { documents.add(old, revisionOf(old)) }
        val trashed = requireNotNull(old.trash(at.plusSeconds(5)))
        database.inTransaction { documents.update(trashed, revisionOf(trashed), amended = false) }
        val missing = DocumentId(UUID.randomUUID())

        val found = database.inTransaction { catalog.find(setOf(guide.id, old.id, missing)) }

        assertThat(found).containsExactlyInAnyOrder(
            GuideCandidate(guide.id, docs.id, "Title of guide", "guide", false),
            GuideCandidate(old.id, docs.id, "Title of old-guide", "old-guide", true),
        )
        assertThat(database.inTransaction { catalog.find(emptySet()) }).isEmpty()
    }
}
