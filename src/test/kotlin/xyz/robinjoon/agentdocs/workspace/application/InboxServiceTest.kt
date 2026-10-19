package xyz.robinjoon.agentdocs.workspace.application

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.agent
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.agentAccess
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.document
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.documentId
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.now
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.ownerAccess
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.space
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.spaceId
import xyz.robinjoon.agentdocs.workspace.application.port.input.InboxItemNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.InboxRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceRepository
import xyz.robinjoon.agentdocs.workspace.application.service.InboxService
import xyz.robinjoon.agentdocs.workspace.domain.Permission
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.inbox.InboxItem
import xyz.robinjoon.agentdocs.workspace.domain.inbox.InboxItemId
import xyz.robinjoon.agentdocs.workspace.domain.inbox.InboxKind
import xyz.robinjoon.agentdocs.workspace.domain.inbox.Notice
import xyz.robinjoon.agentdocs.workspace.domain.inbox.NoticeTarget
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentId
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId
import java.util.UUID

class InboxServiceTest {
    private val inbox = mockk<InboxRepository>()
    private val documents = mockk<DocumentRepository>()
    private val spaces = mockk<SpaceRepository>()
    private val service = InboxService(inbox, documents, spaces, WorkspaceFixtures.clock)
    private val deletedDocument = DocumentId(UUID.randomUUID())

    @Test
    fun `items about deleted documents are left out`() {
        val kept = item(spaceId, documentId)
        every { inbox.list("robin", true, 200) } returns listOf(kept, item(spaceId, deletedDocument))
        every { documents.findByIds(listOf(documentId, deletedDocument)) } returns listOf(document(emptyList(), revision = 1))
        every { spaces.findByIds(setOf(spaceId)) } returns listOf(space)

        val views = service.list(ownerAccess, unreadOnly = true)

        assertThat(views.map { it.item }).containsExactly(kept)
        assertThat(views.single().documentTitle).isEqualTo("Retry design")
    }

    @Test
    fun `an agent has no inbox`() {
        assertThat(service.list(agentAccess(Permission.EDIT, setOf(spaceId)), unreadOnly = false)).isEmpty()
        assertThat(service.unreadCount(agentAccess(Permission.EDIT, setOf(spaceId)))).isZero()
    }

    @Test
    fun `nobody marks another person's item read`() {
        val someoneElses = InboxItem.deliver(InboxItemId(UUID.randomUUID()), "octocat", notice(spaceId, documentId), now)
        every { inbox.findById(someoneElses.id) } returns someoneElses

        assertThatThrownBy { service.markRead(ownerAccess, someoneElses.id) }.isInstanceOf(InboxItemNotFoundException::class.java)
    }

    private fun item(
        space: SpaceId,
        document: DocumentId,
    ): InboxItem = InboxItem.deliver(InboxItemId(UUID.randomUUID()), "robin", notice(space, document), now)

    private fun notice(
        space: SpaceId,
        document: DocumentId,
    ): Notice =
        Notice.of(InboxKind.REPLY, NoticeTarget(space, document, ThreadId(UUID.randomUUID()), CommentId(UUID.randomUUID())), agent, "Done")
}
