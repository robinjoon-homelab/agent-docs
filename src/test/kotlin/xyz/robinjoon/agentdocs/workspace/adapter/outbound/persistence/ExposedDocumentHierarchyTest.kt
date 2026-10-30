package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.support.persistence.MigratedH2Database
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.agent
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.at
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.document
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.person
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.revisionOf
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.space
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentLocation
import xyz.robinjoon.agentdocs.workspace.domain.Actor
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath
import java.time.Instant

// The queries that find parents and descendants by path.
class ExposedDocumentHierarchyTest {
    private val database = MigratedH2Database()
    private val documents = ExposedDocumentRepository()
    private val revisions = ExposedRevisionCatalog()
    private val space = database.inTransaction { ExposedSpaceRepository().add(space("agent-docs")) }
    private val other = database.inTransaction { ExposedSpaceRepository().add(space("other")) }

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `documents under a path are the live ones whose path continues it, shallow first`() {
        add("payments")
        val deep = add("payments/retry/backoff")
        val child = add("payments/retry")
        add("payments-old/notes")
        add("payments_x")
        trash(add("payments/gone"), at.plusSeconds(1))

        val under = database.inTransaction { documents.lockLiveUnder(space.id, DocumentPath("payments")) }

        assertThat(under.map { it.id }).containsExactly(child.id, deep.id)
    }

    @Test
    fun `documents trashed together are the ones under the path trashed at the same instant`() {
        val trashedAt = at.plusSeconds(10)
        trash(add("payments"), trashedAt)
        val together = trash(add("payments/retry"), trashedAt)
        trash(add("payments/alone"), at.plusSeconds(5))
        add("payments/live")

        val found = database.inTransaction { documents.lockTrashedTogether(space.id, DocumentPath("payments"), trashedAt) }

        assertThat(found.map { it.id }).containsExactly(together.id)
    }

    @Test
    fun `the last trashed document at a path is the one trashed most recently`() {
        trash(add("notes"), at.plusSeconds(1))
        val latest = trash(add("notes"), at.plusSeconds(2))
        add("notes")

        val found = database.inTransaction { documents.lockLastTrashedAt(space.id, DocumentPath("notes")) }

        assertThat(found?.id).isEqualTo(latest.id)
        assertThat(database.inTransaction { documents.lockLastTrashedAt(space.id, DocumentPath("other")) }).isNull()
    }

    @Test
    fun `live documents at paths and every live path leave out the trash`() {
        val parent = add("기획")
        add("기획/기능-정의서")
        trash(add("메모"), at.plusSeconds(1))
        val elsewhere = document(other.id, "기획", emptyList())
        database.inTransaction { documents.add(elsewhere, revisionOf(elsewhere)) }

        val atPaths = database.inTransaction { documents.lockLiveAt(space.id, listOf(DocumentPath("기획"), DocumentPath("메모"))) }
        val live = database.inTransaction { documents.livePaths() }

        assertThat(atPaths.map { it.id }).containsExactly(parent.id)
        assertThat(live).containsExactlyInAnyOrder(
            DocumentLocation(space.id, DocumentPath("기획")),
            DocumentLocation(space.id, DocumentPath("기획/기능-정의서")),
            DocumentLocation(other.id, DocumentPath("기획")),
        )
    }

    @Test
    fun `the author of a login is the person behind their latest revision, also one their agent wrote`() {
        val created = add("notes")
        val revised = created.revise(created.header, emptyList(), at.plusSeconds(5))
        database.inTransaction { documents.update(revised, revisionOf(revised, agent), amended = false) }

        assertThat(database.inTransaction { revisions.authorOf("robin") }).isEqualTo(Actor.human(person.userId, "robin"))
        assertThat(database.inTransaction { revisions.authorOf("someone") }).isNull()
    }

    private fun add(path: String): Document {
        val created = document(space.id, path, emptyList())
        return database.inTransaction { documents.add(created, revisionOf(created)) }
    }

    private fun trash(
        document: Document,
        at: Instant,
    ): Document {
        val trashed = requireNotNull(document.trash(at))
        return database.inTransaction { documents.update(trashed, revisionOf(trashed), amended = false) }
    }
}
