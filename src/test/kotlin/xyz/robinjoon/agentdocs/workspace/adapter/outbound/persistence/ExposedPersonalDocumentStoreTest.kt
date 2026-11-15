package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.support.persistence.MigratedH2Database
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.at
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.document
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.revisionOf
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.space
import java.util.UUID

class ExposedPersonalDocumentStoreTest {
    private val database = MigratedH2Database()
    private val personal = ExposedPersonalDocumentStore()

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `visits and favorites preserve each other and belong to one user`() {
        val space = database.inTransaction { ExposedSpaceRepository().add(space("notes")) }
        val document = document(space.id, "memo", emptyList())
        database.inTransaction { ExposedDocumentRepository().add(document, revisionOf(document)) }
        val user = UUID.randomUUID()
        database.inTransaction { personal.recordView(user, document.id, at) }
        database.inTransaction { personal.favorite(user, document.id, at.plusSeconds(1)) }
        database.inTransaction { personal.recordView(user, document.id, at.plusSeconds(2)) }
        val state = database.inTransaction { personal.list(user) }.single()
        assertThat(state.viewedAt).isEqualTo(at.plusSeconds(2))
        assertThat(state.favoriteAt).isEqualTo(at.plusSeconds(1))
        assertThat(database.inTransaction { personal.list(UUID.randomUUID()) }).isEmpty()
        database.inTransaction { personal.favorite(user, document.id, null) }
        val removed = database.inTransaction { personal.list(user) }.single()
        assertThat(removed.favoriteAt).isNull()
        assertThat(removed.viewedAt).isEqualTo(at.plusSeconds(2))
    }
}
