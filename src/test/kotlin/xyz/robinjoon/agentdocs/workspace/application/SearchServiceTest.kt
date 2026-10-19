package xyz.robinjoon.agentdocs.workspace.application

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.agentAccess
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.document
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.documentId
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.now
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.otherSpaceId
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.ownerAccess
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.paragraph
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.space
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.spaceId
import xyz.robinjoon.agentdocs.workspace.application.model.CommentMatch
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.SearchHitKind
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.SearchCatalog
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceRepository
import xyz.robinjoon.agentdocs.workspace.application.service.SearchService
import xyz.robinjoon.agentdocs.workspace.domain.Permission
import xyz.robinjoon.agentdocs.workspace.domain.space.Space
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceProfile
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId
import java.util.UUID

class SearchServiceTest {
    private val catalog = mockk<SearchCatalog>()
    private val documents = mockk<DocumentRepository>()
    private val spaces = mockk<SpaceRepository>()
    private val service = SearchService(catalog, documents, spaces)
    private val diary = Space.create(otherSpaceId, SpaceSlug("diary"), SpaceProfile.of("Diary", "", emptyList()), now)
    private val retry = document(listOf(paragraph("para01", "결제 재시도는 지수 백오프로 한다"), paragraph("para02", "unrelated")), revision = 2)

    @Test
    fun `hits name the title, the matching blocks with a snippet, and matching comments`() {
        val thread = ThreadId(UUID.randomUUID())
        every { spaces.findAll() } returns listOf(space, diary)
        every { catalog.documents(setOf(spaceId, otherSpaceId), "재시도", 50) } returns listOf(documentId)
        every { catalog.comments(setOf(spaceId, otherSpaceId), "재시도", 50) } returns listOf(CommentMatch(documentId, thread, "재시도 간격은?"))
        every { documents.findByIds(listOf(documentId)) } returns listOf(retry)

        val hits = service.search(ownerAccess, " 재시도 ", null)

        assertThat(hits.map { it.kind to it.snippet }).containsExactly(
            SearchHitKind.BLOCK to "결제 재시도는 지수 백오프로 한다",
            SearchHitKind.COMMENT to "재시도 간격은?",
        )
        assertThat(hits.first().blockId?.value).isEqualTo("para01")
    }

    @Test
    fun `an agent searches only the spaces its connection lists`() {
        every { spaces.findAll() } returns listOf(space, diary)
        every { catalog.documents(setOf(otherSpaceId), "x", 50) } returns emptyList()
        every { catalog.comments(setOf(otherSpaceId), "x", 50) } returns emptyList()
        every { documents.findByIds(emptyList()) } returns emptyList()

        assertThat(service.search(agentAccess(Permission.READ, setOf(otherSpaceId)), "x", null)).isEmpty()
        assertThat(service.search(agentAccess(Permission.READ, emptySet()), "x", null)).isEmpty()
    }

    @Test
    fun `an empty search term is refused`() {
        assertThatThrownBy { service.search(ownerAccess, "  ", null) }
            .isInstanceOf(InvalidRequestException::class.java)
            .hasMessage("search for 1 to 100 characters")
    }
}
