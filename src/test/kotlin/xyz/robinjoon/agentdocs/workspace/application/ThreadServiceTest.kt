package xyz.robinjoon.agentdocs.workspace.application

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.application.port.output.ActivityReporter
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.agentAccess
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.context
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.document
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.documentId
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.now
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.owner
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.ownerAccess
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.paragraph
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.space
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.spaceId
import xyz.robinjoon.agentdocs.workspace.application.model.AnchorRequest
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.OperationKey
import xyz.robinjoon.agentdocs.workspace.application.model.OperationRecord
import xyz.robinjoon.agentdocs.workspace.application.model.OperationResultState
import xyz.robinjoon.agentdocs.workspace.application.model.ThreadChange
import xyz.robinjoon.agentdocs.workspace.application.model.ThreadCriteria
import xyz.robinjoon.agentdocs.workspace.application.model.ThreadQuery
import xyz.robinjoon.agentdocs.workspace.application.model.ThreadView
import xyz.robinjoon.agentdocs.workspace.application.port.input.LegacyOperationException
import xyz.robinjoon.agentdocs.workspace.application.port.input.ResultRemovedException
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.InboxRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.OperationStore
import xyz.robinjoon.agentdocs.workspace.application.port.output.RevisionCatalog
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.ThreadRepository
import xyz.robinjoon.agentdocs.workspace.application.service.ThreadService
import xyz.robinjoon.agentdocs.workspace.application.service.WriteJournal
import xyz.robinjoon.agentdocs.workspace.domain.Actor
import xyz.robinjoon.agentdocs.workspace.domain.Permission
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.ChangeSource
import xyz.robinjoon.agentdocs.workspace.domain.document.CodeBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown
import xyz.robinjoon.agentdocs.workspace.domain.thread.Anchor
import xyz.robinjoon.agentdocs.workspace.domain.thread.AnchorResolution
import xyz.robinjoon.agentdocs.workspace.domain.thread.Comment
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentId
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentThread
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadActionException
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadLocation
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadStatus
import java.util.UUID

class ThreadServiceTest {
    private val threads = mockk<ThreadRepository>()
    private val documents = mockk<DocumentRepository>()
    private val spaces = mockk<SpaceRepository>()
    private val inbox = mockk<InboxRepository>()
    private val activity = mockk<ActivityReporter>()
    private val operations = mockk<OperationStore>()
    private val revisions = mockk<RevisionCatalog>()
    private val service =
        ThreadService(threads, documents, spaces, inbox, revisions, WriteJournal(activity, operations, WorkspaceFixtures.clock))
    private val intro = paragraph("intro1", "retry after a delay, then retry again")
    private val current = document(listOf(paragraph("title1", "Overview"), intro, paragraph("outro1", "Done")), revision = 5)
    private val threadId = ThreadId(UUID.fromString("66666666-6666-4666-8666-666666666666"))
    private val agentAccess = agentAccess(Permission.EDIT, setOf(spaceId))

    init {
        every { documents.pending(any()) } returns null
        every { activity.record(any()) } just runs
        every { inbox.addAll(any()) } just runs
        every { documents.confirmPending(any()) } answers { firstArg() }
    }

    @Test
    fun `a quote that occurs once is anchored at its offsets`() {
        every { documents.findById(documentId) } returns current
        every { documents.findByIdForUpdate(documentId) } returns current
        every { threads.add(any()) } answers { firstArg() }

        val view = service.open(context(ownerAccess), documentId, AnchorRequest.Text(intro.id, "a delay", null), "How long?")

        assertThat(view.thread.location.anchor).isEqualTo(Anchor.Text(intro.id, 12, 19, "a delay"))
        assertThat(view.resolution).isEqualTo(AnchorResolution.Attached(12, 19))
        assertThat(view.thread.location.anchoredRevision).isEqualTo(5)
    }

    @Test
    fun `a thread on a whole block keeps the start of the block's text as its quote`() {
        val diagram = CodeBlock(BlockId("diag01"), "mermaid", "flowchart LR\n" + "  A --> B\n".repeat(40))
        every { documents.findByIdForUpdate(documentId) } returns document(listOf(intro, diagram), revision = 5)
        every { threads.add(any()) } answers { firstArg() }

        val view = service.open(context(ownerAccess), documentId, AnchorRequest.Whole(diagram.id), "Is the order right?")

        assertThat(view.thread.location.anchor).isEqualTo(Anchor.Whole(diagram.id, diagram.code.take(200)))
    }

    @Test
    fun `a quote that occurs more than once needs its start`() {
        every { documents.findById(documentId) } returns current
        every { documents.findByIdForUpdate(documentId) } returns current

        assertThatThrownBy { service.open(context(ownerAccess), documentId, AnchorRequest.Text(intro.id, "retry", null), "Which one?") }
            .isInstanceOf(InvalidRequestException::class.java)
            .hasMessage("the quote occurs 2 times in block intro1")
    }

    @Test
    fun `an agent cannot resolve a thread a person opened`() {
        every { threads.findByIdForUpdate(threadId) } returns thread(owner)
        every { documents.findById(documentId) } returns current
        every { documents.findByIdForUpdate(documentId) } returns current

        assertThatThrownBy {
            service.change(
                context(agentAccess, ChangeSource.MCP),
                threadId,
                ThreadChange(status = ThreadStatus.RESOLVED),
            )
        }.isInstanceOfSatisfying(ThreadActionException::class.java) {
            assertThat(it.fix).isEqualTo("mark the thread answered; the person who opened it resolves it")
        }
    }

    @Test
    fun `an agent reply can mark the thread answered and a person's reply opens it again`() {
        every { threads.findByIdForUpdate(threadId) } returns thread(owner)
        every { documents.findById(documentId) } returns current
        every { documents.findByIdForUpdate(documentId) } returns current
        every { threads.update(any()) } answers { firstArg() }

        val answered = service.reply(context(agentAccess, ChangeSource.MCP), threadId, "Changed to 30 seconds.", markAnswered = true)
        every { threads.findByIdForUpdate(threadId) } returns answered.thread
        val reopened = service.reply(context(ownerAccess), threadId, "Make it 60.", markAnswered = false)

        assertThat(answered.thread.status).isEqualTo(ThreadStatus.ANSWERED)
        assertThat(reopened.thread.status).isEqualTo(ThreadStatus.OPEN)
        assertThat(reopened.thread.comments.map { it.body }).containsExactly("How long?", "Changed to 30 seconds.", "Make it 60.")
    }

    @Test
    fun `a query result carries the anchored block and its neighbors`() {
        val found = thread(owner)
        every { threads.find(ThreadCriteria(query(), ThreadQuery.MAX_RESULTS)) } returns listOf(found)
        every { documents.findByIds(listOf(documentId)) } returns listOf(current)
        every { spaces.findByIds(setOf(spaceId)) } returns listOf(space)

        val view = service.query(agentAccess, query(spaces = null)).single()

        assertThat(view.context?.anchorBlock).isEqualTo("<!-- b:intro1 h:${hashOf(intro)} -->\nretry after a delay, then retry again")
        assertThat(view.context?.previousBlock).startsWith("<!-- b:title1 ")
        assertThat(view.context?.nextBlock).endsWith("\nDone")
    }

    @Test
    fun `a commenter who may only read is refused`() {
        every { documents.findById(documentId) } returns current
        every { documents.findByIdForUpdate(documentId) } returns current

        assertThatThrownBy {
            service.open(context(agentAccess(Permission.READ, setOf(spaceId)), ChangeSource.MCP), documentId, AnchorRequest.Document, "Hi")
        }.hasMessage("commenting needs comment permission")
    }

    @Test
    fun `a legacy comment key reports the successful operation without reapplying it`() {
        val opened = thread(owner)
        val comment = opened.comments.first().id
        every { operations.find(OperationKey(WorkspaceFixtures.ownerId.toString(), "op-edit")) } returns
            OperationRecord("comment.edit", comment.value, null)
        every { threads.findByCommentForUpdate(comment) } returns opened
        every { documents.findById(documentId) } returns current
        every { documents.findByIdForUpdate(documentId) } returns current

        repeat(2) {
            assertThatThrownBy { service.editComment(context(ownerAccess, operationId = "op-edit"), comment, "Edited again?") }
                .isInstanceOf(LegacyOperationException::class.java)
                .hasMessageContaining("already succeeded")
        }
        assertThat(opened.comments.first().body).isEqualTo("How long?")
        assertThat(current.version).isEqualTo(5)
    }

    @Test
    fun `a legacy thread key cannot reapply the old operation`() {
        every { operations.find(OperationKey(WorkspaceFixtures.connectionId.toString(), "op-answer")) } returns
            OperationRecord("thread.change", threadId.value, null)
        every { threads.findById(threadId) } returns thread(owner)
        every { documents.findById(documentId) } returns current
        every { documents.findByIdForUpdate(documentId) } returns current

        repeat(2) {
            assertThatThrownBy {
                service.change(context(agentAccess, ChangeSource.MCP, "op-answer"), threadId, ThreadChange(status = ThreadStatus.ANSWERED))
            }.isInstanceOf(LegacyOperationException::class.java)
        }
    }

    @Test
    fun `a replay returns the original thread and anchor resolution after later replies and edits`() {
        val original = thread(owner)
        val first = ThreadView(original, AnchorResolution.Attached(12, 19), null, original.comments.first().id)
        every { operations.find(any()) } returns OperationRecord("thread.open", threadId.value, null, threadView = first)
        every { threads.findById(threadId) } returns
            original.reply(Comment.write(CommentId(UUID.randomUUID()), owner, "Later reply", now), now)
        every { documents.findById(documentId) } returns document(emptyList(), revision = 6)

        val replayed = service.open(context(ownerAccess, operationId = "same"), documentId, AnchorRequest.Document, "Ignored")

        assertThat(replayed).isEqualTo(first)
        assertThat(replayed.thread.comments).hasSize(1)
        assertThat(replayed.resolution).isEqualTo(AnchorResolution.Attached(12, 19))
    }

    @Test
    fun `a replayed edit still requires the current comment author`() {
        val original = thread(owner)
        val comment = original.comments.first().id
        val snapshot = ThreadView(original, AnchorResolution.Attached(12, 19), null)
        every { operations.find(any()) } returns OperationRecord("comment.edit", comment.value, null, threadView = snapshot)
        every { threads.findByCommentForUpdate(comment) } returns
            thread(agentAccess.actor).let {
                CommentThread.restore(
                    it.id,
                    it.location,
                    it.progress,
                    listOf(Comment.write(comment, agentAccess.actor, "Different author", now)),
                )
            }
        every { documents.findById(documentId) } returns current

        assertThatThrownBy { service.editComment(context(ownerAccess, operationId = "same"), comment, "Ignored") }
            .isInstanceOf(ThreadActionException::class.java)
            .hasMessage("only the author can change a comment")
    }

    @Test
    fun `a deleted comment replay returns its successful result with no history`() {
        val original = thread(owner)
        val comment = original.comments.first().id
        val deleted = original.editComment(comment, owner, "Secret edit", now).deleteComment(comment, owner, now)
        val snapshot = ThreadView(deleted, AnchorResolution.Attached(12, 19), null)
        every { operations.find(any()) } returns OperationRecord("comment.delete", comment.value, null, threadView = snapshot)
        every { threads.findByCommentForUpdate(comment) } returns deleted
        every { documents.findById(documentId) } returns current

        val replayed = service.deleteComment(context(ownerAccess, operationId = "same"), comment)

        assertThat(replayed).isEqualTo(snapshot)
        assertThat(replayed.thread.comment(comment)?.body).isEmpty()
        assertThat(
            replayed.thread
                .comment(comment)
                ?.history
                ?.edits,
        ).isEmpty()
    }

    @Test
    fun `opening a thread anchors to the confirmed pending revision`() {
        val confirmed = current.revise(current.header, current.blocks, now)
        every { documents.findByIdForUpdate(documentId) } returns current
        every { documents.confirmPending(current) } returns confirmed
        every { threads.add(any()) } answers { firstArg() }

        val view = service.open(context(ownerAccess), documentId, AnchorRequest.Document, "Question")

        assertThat(view.thread.location.anchoredRevision).isEqualTo(confirmed.revision)
    }

    @Test
    fun `a replayed thread write is refused after comment permission is revoked`() {
        val original = thread(owner)
        val snapshot = ThreadView(original, AnchorResolution.Attached(12, 19), null)
        every { operations.find(any()) } returns OperationRecord("thread.open", threadId.value, null, threadView = snapshot)
        every { threads.findById(threadId) } returns original
        val readOnly = agentAccess(Permission.READ, setOf(spaceId))

        assertThatThrownBy {
            service.open(context(readOnly, operationId = "same"), documentId, AnchorRequest.Document, "Ignored")
        }.hasMessage("commenting needs comment permission")
    }

    @Test
    fun `an anchor change uses the confirmed pending revision`() {
        val original = thread(owner)
        val confirmed = current.revise(current.header, current.blocks, now)
        every { threads.findById(threadId) } returns original
        every { documents.findByIdForUpdate(documentId) } returns current
        every { threads.findByIdForUpdate(threadId) } returns original
        every { documents.confirmPending(current) } returns confirmed
        every { threads.update(any()) } answers { firstArg() }

        val moved = service.change(context(ownerAccess), threadId, ThreadChange(anchor = AnchorRequest.Document))

        assertThat(moved.thread.location.anchor).isEqualTo(Anchor.Document)
        assertThat(moved.thread.location.anchoredRevision).isEqualTo(confirmed.revision)
    }

    @Test
    fun `a removed comment result reports prior success after checking current author`() {
        val original = thread(owner)
        val comment = original.comments.first().id
        val deleted = original.deleteComment(comment, owner, now)
        val record = OperationRecord("comment.delete", comment.value, null, state = OperationResultState.REMOVED)
        every { operations.find(any()) } returns record
        every { threads.findByCommentForUpdate(comment) } returns deleted
        every { documents.findById(documentId) } returns current

        assertThatThrownBy { service.deleteComment(context(ownerAccess, operationId = "removed"), comment) }
            .isInstanceOfSatisfying(ResultRemovedException::class.java) { assertThat(it.record).isEqualTo(record) }
        assertThat(deleted.comment(comment)?.history?.edits).isEmpty()
    }

    @Test
    fun `removed results do not reveal prior success after comment permission is revoked`() {
        val record = OperationRecord("thread.change", threadId.value, null, state = OperationResultState.REMOVED)
        every { operations.find(any()) } returns record
        every { threads.findById(threadId) } returns thread(owner)
        val readOnly = agentAccess(Permission.READ, setOf(spaceId))

        assertThatThrownBy {
            service.change(context(readOnly, operationId = "removed"), threadId, ThreadChange(status = ThreadStatus.ANSWERED))
        }.hasMessage("commenting needs comment permission")
    }

    @Test
    fun `claim opId replay returns its first claim time after release`() {
        var saved = thread(owner)
        var operation: OperationRecord? = null
        every { operations.find(any()) } answers { operation }
        every { operations.save(any(), any(), any()) } answers {
            operation = secondArg()
            Unit
        }
        every { threads.findByIdForUpdate(threadId) } answers { saved }
        every { threads.findById(threadId) } answers { saved }
        every { documents.findById(documentId) } returns current
        every { threads.update(any()) } answers {
            saved = firstArg()
            saved
        }
        val first = service.claim(context(agentAccess, operationId = "claim-once"), threadId, "acquire")
        service.claim(context(agentAccess), threadId, "release")
        val replay = service.claim(context(agentAccess, operationId = "claim-once"), threadId, "acquire")
        assertThat(saved.collaboration.claim).isNull()
        assertThat(replay).isEqualTo(first)
        assertThat(
            replay.thread.collaboration.claim
                ?.expiresAt,
        ).isEqualTo(now.plusSeconds(900))
    }

    @Test
    fun `another connection cannot reply or mark a claimed thread answered while humans can reply`() {
        val other =
            Actor.agent(
                owner.userId,
                owner.login,
                xyz.robinjoon.agentdocs.workspace.domain
                    .AgentIdentity(UUID.randomUUID(), "other"),
            )
        val original = thread(owner)
        val claimed = original.collaborate(original.collaboration.claim(other, now, "acquire"), now)
        every { threads.findByIdForUpdate(threadId) } returns claimed
        every { documents.findById(documentId) } returns current
        every { threads.update(any()) } answers { firstArg() }
        assertThatThrownBy { service.reply(context(agentAccess), threadId, "mine", false) }.isInstanceOf(ThreadActionException::class.java)
        assertThatThrownBy { service.change(context(agentAccess), threadId, ThreadChange(status = ThreadStatus.ANSWERED)) }
            .isInstanceOf(ThreadActionException::class.java)
        assertThat(
            service
                .reply(context(ownerAccess), threadId, "human", false)
                .thread.comments
                .last()
                .body,
        ).isEqualTo("human")
    }

    private fun query(spaces: Set<SpaceId>? = setOf(spaceId)): ThreadQuery =
        ThreadQuery(spaces, null, setOf(ThreadStatus.OPEN), null, null, null)

    private fun hashOf(block: Block): String = BlockMarkdown.hash(block)

    private fun thread(opener: Actor): CommentThread {
        val location = ThreadLocation(spaceId, documentId, Anchor.Text(BlockId("intro1"), 12, 19, "a delay"), 5)
        return CommentThread.open(threadId, location, Comment.write(CommentId(UUID.randomUUID()), opener, "How long?", now), now)
    }
}
