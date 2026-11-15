package xyz.robinjoon.agentdocs.workspace.application.service

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.ownerAccess
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.space
import xyz.robinjoon.agentdocs.workspace.application.model.NavigationDocument
import xyz.robinjoon.agentdocs.workspace.application.model.PersonalDocument
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.NavigationCatalog
import xyz.robinjoon.agentdocs.workspace.application.port.output.PersonalDocumentStore
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceRepository
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class NavigationServiceTest {
    private val personal = mockk<PersonalDocumentStore>()
    private val catalog = mockk<NavigationCatalog>()
    private val documents = mockk<DocumentRepository>()
    private val spaces = mockk<SpaceRepository>()
    private val now = Instant.parse("2026-11-16T00:00:00Z")
    private val service = NavigationService(personal, catalog, documents, spaces, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `recent applies visible documents before count and limit and keeps visit order`() {
        val hidden = DocumentId(UUID.randomUUID())
        val older = entry("older")
        val newer = entry("newer")
        every { spaces.findAll() } returns listOf(space)
        every { catalog.documents(setOf(space.id)) } returns listOf(older, newer)
        every { personal.list(ownerAccess.actor.userId) } returns
            listOf(
                PersonalDocument(hidden, now.plusSeconds(1), null),
                PersonalDocument(older.id, now.minusSeconds(1), null),
                PersonalDocument(newer.id, now, null),
            )
        val result = service.recent(ownerAccess, 1)
        assertThat(result.documents).containsExactly(newer)
        assertThat(result.total).isEqualTo(2)
    }

    @Test
    fun `missing target cannot expose backlink list or count`() {
        val id = DocumentId(UUID.randomUUID())
        every { documents.findById(id) } returns null
        assertThatThrownBy { service.backlinks(ownerAccess, id, 20) }.isInstanceOf(DocumentNotFoundException::class.java)
    }

    private fun entry(title: String) =
        NavigationDocument(
            DocumentId(UUID.randomUUID()),
            space.id,
            space.slug.value,
            space.profile.name,
            title,
            title,
        )
}
