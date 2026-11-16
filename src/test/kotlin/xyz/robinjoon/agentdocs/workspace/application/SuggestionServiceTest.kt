package xyz.robinjoon.agentdocs.workspace.application

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.application.port.output.ActivityReporter
import xyz.robinjoon.agentdocs.workspace.application.model.AnchorRequest
import xyz.robinjoon.agentdocs.workspace.application.model.SuggestionRequest
import xyz.robinjoon.agentdocs.workspace.application.port.input.PermissionDeniedException
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.InboxRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.OperationStore
import xyz.robinjoon.agentdocs.workspace.application.port.output.ThreadRepository
import xyz.robinjoon.agentdocs.workspace.application.service.SuggestionService
import xyz.robinjoon.agentdocs.workspace.application.service.WriteJournal
import xyz.robinjoon.agentdocs.workspace.domain.Permission
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown
import xyz.robinjoon.agentdocs.workspace.domain.thread.Anchor
import xyz.robinjoon.agentdocs.workspace.domain.thread.Comment
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentId
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentThread
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadLocation
import java.util.UUID

class SuggestionServiceTest {
    private val documents = mockk<DocumentRepository>()
    private val threads = mockk<ThreadRepository>()
    private val operations = mockk<OperationStore>()
    private val activity = mockk<ActivityReporter>()
    private val inbox = mockk<InboxRepository>()
    private val service = SuggestionService(threads, documents, WriteJournal(activity, operations, WorkspaceFixtures.clock), inbox)
    private val block = WorkspaceFixtures.paragraph("intro1", "hello world")
    private var document = WorkspaceFixtures.document(listOf(block), 3)
    private val now = WorkspaceFixtures.now
    private val owner = WorkspaceFixtures.owner
    private var thread =
        CommentThread.open(
            ThreadId(UUID.randomUUID()),
            ThreadLocation(
                document.spaceId,
                document.id,
                Anchor.Whole(block.id, block.plainText()),
                3,
            ),
            Comment.write(CommentId(UUID.randomUUID()), owner, "Please update", now),
            now,
        )

    init {
        every { documents.pending(any()) } returns null
        every { threads.findById(any()) } answers { thread }
        every { threads.findByIdForUpdate(any()) } answers { thread }
        every { threads.findByComment(any()) } answers { thread }
        every { threads.update(any()) } answers {
            thread = firstArg()
            thread
        }
        every { documents.findByIdForUpdate(any()) } answers { document }
        every { documents.confirmPending(any()) } answers { firstArg() }
        every { documents.update(any(), any(), any()) } answers {
            document = firstArg()
            document
        }
        every { activity.record(any()) } just runs
        every { inbox.addAll(any()) } just runs
    }

    @Test
    fun `accept applies one range and connects the decision and immutable revision`() {
        val context = WorkspaceFixtures.context(WorkspaceFixtures.ownerAccess)
        val proposed =
            service.suggest(
                context,
                thread.id,
                SuggestionRequest(
                    AnchorRequest.Text(block.id, "world", 6),
                    BlockMarkdown.hash(block),
                    "world",
                    "friend",
                ),
            )
        val comment = requireNotNull(proposed.written)
        val accepted = service.decide(context, comment, true)
        assertThat(document.blocks.single().plainText()).isEqualTo("hello friend")
        assertThat(document.revision).isEqualTo(4)
        assertThat(accepted.thread.progress.linkedRevisions).containsExactly(4)
        assertThat(
            accepted.thread.collaboration.suggestions
                .single()
                .decision
                ?.revision,
        ).isEqualTo(4)
    }

    @Test
    fun `reject changes no document content and agents cannot decide`() {
        val context = WorkspaceFixtures.context(WorkspaceFixtures.ownerAccess)
        val proposed =
            service.suggest(
                context,
                thread.id,
                SuggestionRequest(
                    AnchorRequest.Whole(block.id),
                    BlockMarkdown.hash(block),
                    "hello world",
                    "new",
                ),
            )
        val comment = requireNotNull(proposed.written)
        val agent = WorkspaceFixtures.context(WorkspaceFixtures.agentAccess(Permission.EDIT, setOf(document.spaceId)))
        assertThatThrownBy { service.decide(agent, comment, true) }.isInstanceOf(PermissionDeniedException::class.java)
        val rejected = service.decide(context, comment, false)
        assertThat(document.blocks).containsExactly(block)
        assertThat(document.revision).isEqualTo(3)
        assertThat(
            rejected.thread.collaboration.suggestions
                .single()
                .decision
                ?.accepted,
        ).isFalse()
    }
}
