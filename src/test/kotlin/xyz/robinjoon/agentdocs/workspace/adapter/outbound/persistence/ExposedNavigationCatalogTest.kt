package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.support.persistence.MigratedH2Database
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.at
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.document
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.paragraph
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.revisionOf
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.space

class ExposedNavigationCatalogTest {
    private val database = MigratedH2Database()
    private val catalog = ExposedNavigationCatalog()

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `catalog keeps archived spaces and returns only actual links in visible current documents`() {
        val visible = database.inTransaction { ExposedSpaceRepository().add(space("visible").archive(at)) }
        val hidden = database.inTransaction { ExposedSpaceRepository().add(space("hidden")) }
        val target = document(visible.id, "target", emptyList())
        val source = document(visible.id, "source", listOf(paragraph("source0001", "[target](doc:${target.id})")))
        val secret = document(hidden.id, "secret", listOf(paragraph("secret0001", "[target](doc:${target.id})")))
        database.inTransaction {
            val repository = ExposedDocumentRepository()
            listOf(target, source, secret).forEach { repository.add(it, revisionOf(it)) }
        }
        val found = database.inTransaction { catalog.linkingTo(target.id, setOf(visible.id)) }
        assertThat(found.map { it.id }).containsExactly(source.id)
        assertThat(database.inTransaction { catalog.documents(emptySet()) }).isEmpty()
    }
}
