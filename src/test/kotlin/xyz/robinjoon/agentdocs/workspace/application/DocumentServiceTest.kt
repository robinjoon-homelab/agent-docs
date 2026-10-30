package xyz.robinjoon.agentdocs.workspace.application

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import xyz.robinjoon.agentdocs.application.port.output.ActivityReporter
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.agent
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.agentAccess
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.context
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.document
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.documentId
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.header
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.now
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.owner
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.ownerAccess
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.paragraph
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.revision
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.space
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.spaceId
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentDraft
import xyz.robinjoon.agentdocs.workspace.application.model.HeaderChange
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.OperationKey
import xyz.robinjoon.agentdocs.workspace.application.model.OperationRecord
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.input.PermissionDeniedException
import xyz.robinjoon.agentdocs.workspace.application.port.input.StaleRevisionException
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.OperationStore
import xyz.robinjoon.agentdocs.workspace.application.port.output.RevisionCatalog
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.ThreadRepository
import xyz.robinjoon.agentdocs.workspace.application.service.DocumentService
import xyz.robinjoon.agentdocs.workspace.application.service.WriteJournal
import xyz.robinjoon.agentdocs.workspace.domain.Permission
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.ChangeSource
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentProperties
import xyz.robinjoon.agentdocs.workspace.domain.document.PathTakenException
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.BlockConflict
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.EditConflictException
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.EditOperation
import xyz.robinjoon.agentdocs.workspace.domain.document.history.ChangeOrigin
import xyz.robinjoon.agentdocs.workspace.domain.document.history.Revision
import xyz.robinjoon.agentdocs.workspace.domain.document.history.RevisionContent
import xyz.robinjoon.agentdocs.workspace.domain.document.history.RevisionKind
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown

// Every port is a strict mock: a call a test does not prepare fails the test.
class DocumentServiceTest {
    private val documents = mockk<DocumentRepository>()
    private val revisions = mockk<RevisionCatalog>()
    private val spaces = mockk<SpaceRepository>()
    private val threads = mockk<ThreadRepository>()
    private val activity = mockk<ActivityReporter>()
    private val operations = mockk<OperationStore>()
    private val service =
        DocumentService(documents, revisions, spaces, threads, WriteJournal(activity, operations, WorkspaceFixtures.clock))
    private val first = paragraph("first1", "First")
    private val second = paragraph("second", "Second")
    private val current = document(listOf(first, second), revision = 3)
    private val byAgent = ChangeOrigin(agent, ChangeSource.MCP)
    private val byOwnerOnWeb = ChangeOrigin(owner, ChangeSource.WEB)

    init {
        every { activity.record(any()) } just runs
    }

    @Test
    fun `creating a document starts its history at revision one with every block changed`() {
        every { spaces.findBySlug(space.slug) } returns space
        every { documents.findByPath(spaceId, DocumentPath("notes/today")) } returns null
        every { documents.lockLiveAt(spaceId, listOf(DocumentPath("notes"))) } returns emptyList()
        every { documents.add(any(), any()) } answers { firstArg() }

        val draft = DocumentDraft(DocumentPath("notes/today"), " Today ", DocumentProperties.initial, listOf(first, second))
        val written = service.create(context(ownerAccess), space.slug, draft)

        assertThat(written.revision).isEqualTo(1)
        assertThat(written.document.header.title).isEqualTo("Today")
        assertThat(written.changedBlocks).containsExactly(first.id, second.id)
    }

    @Test
    fun `a path another document already uses is rejected`() {
        every { spaces.findBySlug(space.slug) } returns space
        every { documents.findByPath(spaceId, DocumentPath("payments/retry")) } returns current

        val draft = DocumentDraft(DocumentPath("payments/retry"), "Copy", DocumentProperties.initial, emptyList())

        assertThatThrownBy { service.create(context(ownerAccess), space.slug, draft) }.isInstanceOf(PathTakenException::class.java)
    }

    @Test
    fun `saving on the latest revision adds a revision listing only the changed block`() {
        every { documents.findByIdForUpdate(documentId) } returns current
        every { revisions.find(documentId, 3) } returns revision(3, current.blocks, byAgent, now.minusSeconds(5))
        every { documents.update(any(), any(), false) } answers { firstArg() }

        val written = service.save(context(ownerAccess), documentId, 3, listOf(first, paragraph("second", "Second, edited")))

        assertThat(written.revision).isEqualTo(4)
        assertThat(written.amended).isFalse()
        assertThat(written.merged).isFalse()
        assertThat(written.changedBlocks).containsExactly(second.id)
    }

    @Test
    fun `a person's web save within thirty seconds of their own web save replaces that revision`() {
        every { documents.findByIdForUpdate(documentId) } returns current
        every { revisions.find(documentId, 3) } returns revision(3, current.blocks, byOwnerOnWeb, now.minusSeconds(20))
        every { revisions.find(documentId, 2) } returns revision(2, listOf(first), byAgent, now.minusSeconds(600))
        every { documents.update(any(), any(), true) } answers { firstArg() }

        val written = service.save(context(ownerAccess), documentId, 3, listOf(first, paragraph("second", "Second, typed on")))

        assertThat(written.revision).isEqualTo(3)
        assertThat(written.amended).isTrue()
        assertThat(written.document.blocks.last()).isEqualTo(paragraph("second", "Second, typed on"))
    }

    @ParameterizedTest
    @EnumSource(value = RevisionKind::class, names = ["RESTORE", "TRANSITION"])
    fun `a person's save right after a restore or a trash move adds a revision instead of rewriting it`(kind: RevisionKind) {
        val latest = revision(3, current.blocks, byOwnerOnWeb, now.minusSeconds(10))
        every { documents.findByIdForUpdate(documentId) } returns current
        every { revisions.find(documentId, 3) } returns latest.copy(change = latest.change.withKind(kind))
        every { documents.update(any(), any(), false) } answers { firstArg() }

        val written = service.save(context(ownerAccess), documentId, 3, listOf(first, paragraph("second", "Second, typed on")))

        assertThat(written.revision).isEqualTo(4)
        assertThat(written.amended).isFalse()
    }

    @Test
    fun `an agent never groups its writes into an earlier revision`() {
        every { documents.findByIdForUpdate(documentId) } returns current
        every { documents.update(any(), any(), false) } answers { firstArg() }

        val written = service.save(context(agentAccess(Permission.EDIT, setOf(spaceId)), ChangeSource.MCP), documentId, 3, listOf(first))

        assertThat(written.revision).isEqualTo(4)
        assertThat(written.changedBlocks).containsExactly(second.id)
    }

    @Test
    fun `changes others made since the base revision are kept when they touch other blocks`() {
        val changedByOthers = document(listOf(paragraph("first1", "First, by the agent"), second), revision = 3)
        every { documents.findByIdForUpdate(documentId) } returns changedByOthers
        every { revisions.find(documentId, 2) } returns revision(2, listOf(first, second), byOwnerOnWeb, now.minusSeconds(120))
        every { documents.update(any(), any(), false) } answers { firstArg() }

        val written = service.save(context(ownerAccess), documentId, 2, listOf(first, paragraph("second", "Second, by me")))

        assertThat(
            written.document.blocks,
        ).containsExactly(paragraph("first1", "First, by the agent"), paragraph("second", "Second, by me"))
        assertThat(written.revision).isEqualTo(4)
        assertThat(written.merged).isTrue()
    }

    @Test
    fun `a block both sides changed stops the save and reports the current block`() {
        val theirs = paragraph("second", "Second, by the agent")
        every { documents.findByIdForUpdate(documentId) } returns document(listOf(first, theirs), revision = 3)
        every { revisions.find(documentId, 2) } returns revision(2, listOf(first, second), byOwnerOnWeb, now.minusSeconds(120))

        assertThatThrownBy { service.save(context(ownerAccess), documentId, 2, listOf(first, paragraph("second", "Second, by me"))) }
            .isInstanceOfSatisfying(EditConflictException::class.java) {
                assertThat(it.conflicts).containsExactly(BlockConflict(second.id, BlockMarkdown.hash(theirs), "Second, by the agent"))
            }
    }

    @Test
    fun `saving the same blocks adds no revision`() {
        every { documents.findByIdForUpdate(documentId) } returns current

        val written = service.save(context(ownerAccess), documentId, 3, current.blocks)

        assertThat(written.unchanged).isTrue()
        assertThat(written.revision).isEqualTo(3)
    }

    @Test
    fun `a base revision the document never had is rejected`() {
        every { documents.findByIdForUpdate(documentId) } returns current

        assertThatThrownBy { service.save(context(ownerAccess), documentId, 9, current.blocks) }
            .isInstanceOf(InvalidRequestException::class.java)
            .hasMessage("baseRevision 9 does not exist; the document is at revision 3")
    }

    @Test
    fun `an agent that may only read cannot write and one without the space cannot find the document`() {
        every { documents.findByIdForUpdate(documentId) } returns current

        assertThatThrownBy {
            service.save(
                context(agentAccess(Permission.READ, setOf(spaceId)), ChangeSource.MCP),
                documentId,
                3,
                listOf(first),
            )
        }.isInstanceOf(PermissionDeniedException::class.java)
            .hasMessage("editing a document needs edit permission")
        assertThatThrownBy {
            service.save(
                context(agentAccess(Permission.EDIT, emptySet()), ChangeSource.MCP),
                documentId,
                3,
                listOf(first),
            )
        }.isInstanceOf(DocumentNotFoundException::class.java)
    }

    @Test
    fun `an edit whose block changed since it was read is refused as a whole`() {
        every { documents.findByIdForUpdate(documentId) } returns current
        val operations = listOf(EditOperation.Delete(first.id, BlockMarkdown.hash(first)), EditOperation.Delete(second.id, "00000000"))

        assertThatThrownBy {
            service.edit(
                context(agentAccess(Permission.EDIT, setOf(spaceId)), ChangeSource.MCP),
                documentId,
                operations,
                null,
            )
        }.isInstanceOfSatisfying(EditConflictException::class.java) {
            assertThat(
                it.conflicts.map { c ->
                    c.blockId
                },
            ).containsExactly(second.id)
        }
    }

    @Test
    fun `a retried write answers with the revision the first attempt produced`() {
        val key = OperationKey(WorkspaceFixtures.connectionId.toString(), "op-1")
        every { operations.find(key) } returns OperationRecord("document.edit", documentId.value, 7)
        every { documents.findById(documentId) } returns document(listOf(first), revision = 9)
        every { revisions.find(documentId, 7) } returns revision(7, listOf(first), byAgent, now.minusSeconds(30))

        val agentContext = context(agentAccess(Permission.EDIT, setOf(spaceId)), ChangeSource.MCP, "op-1")
        val written = service.edit(agentContext, documentId, listOf(EditOperation.Delete(first.id, BlockMarkdown.hash(first))), null)

        assertThat(written.revision).isEqualTo(7)
        assertThat(written.changedBlocks).containsExactly(BlockId("first1"))
    }

    @Test
    fun `an operation id reused for another kind of write is rejected`() {
        val key = OperationKey(WorkspaceFixtures.connectionId.toString(), "op-1")
        every { operations.find(key) } returns OperationRecord("thread.open", documentId.value, null)

        val agentContext = context(agentAccess(Permission.EDIT, setOf(spaceId)), ChangeSource.MCP, "op-1")

        assertThatThrownBy { service.edit(agentContext, documentId, emptyList(), null) }
            .isInstanceOf(InvalidRequestException::class.java)
            .hasMessage("opId op-1 was already used for a different operation")
    }

    @Test
    fun `trashing twice changes nothing the second time`() {
        every { documents.findByIdForUpdate(documentId) } returns document(listOf(first), revision = 4, trashedAt = now.minusSeconds(10))

        val written = service.trash(context(ownerAccess), documentId)

        assertThat(written.unchanged).isTrue()
    }

    @Test
    fun `a retried trash move answers with the revision the first attempt produced`() {
        every { operations.find(OperationKey(WorkspaceFixtures.ownerId.toString(), "op-trash")) } returns
            OperationRecord("document.trash", documentId.value, 4)
        every { documents.findById(documentId) } returns document(listOf(first), revision = 4, trashedAt = now.minusSeconds(5))
        every { revisions.find(documentId, 4) } returns revision(4, listOf(first), byOwnerOnWeb, now.minusSeconds(5))

        val written = service.trash(context(ownerAccess, operationId = "op-trash"), documentId)

        assertThat(written.revision).isEqualTo(4)
        assertThat(written.unchanged).isFalse()
    }

    @Test
    fun `a retried permanent delete finds the document already gone and succeeds`() {
        every { operations.find(OperationKey(WorkspaceFixtures.ownerId.toString(), "op-purge")) } returns
            OperationRecord("document.purge", documentId.value, null)
        every { documents.findByIdForUpdate(documentId) } returns null

        assertThatCode { service.purge(context(ownerAccess, operationId = "op-purge"), documentId) }.doesNotThrowAnyException()
    }

    @Test
    fun `only the owner deletes permanently and only from the trash`() {
        every { documents.findByIdForUpdate(documentId) } returns current

        assertThatThrownBy { service.purge(context(ownerAccess), documentId) }
            .isInstanceOf(InvalidRequestException::class.java)
            .hasMessage("only a document in the trash can be deleted permanently")
        assertThatThrownBy { service.purge(context(agentAccess(Permission.EDIT, setOf(spaceId)), ChangeSource.MCP), documentId) }
            .isInstanceOf(PermissionDeniedException::class.java)
    }

    @Test
    fun `restoring writes the old blocks as a new revision`() {
        every { documents.findByIdForUpdate(documentId) } returns current
        every { revisions.find(documentId, 1) } returns revision(1, listOf(first), byOwnerOnWeb, now.minusSeconds(3_600))
        every { documents.update(any(), any(), false) } answers { firstArg() }

        val written = service.restore(context(ownerAccess), documentId, 1, null)

        assertThat(written.revision).isEqualTo(4)
        assertThat(written.document.blocks).containsExactly(first)
    }

    @Test
    fun `a header change on an older revision is refused when someone changed that field since`() {
        val base = revision(2, current.blocks, byAgent, now.minusSeconds(600))
        every { documents.findByIdForUpdate(documentId) } returns current
        every { revisions.find(documentId, 2) } returns
            Revision(documentId, 2, RevisionContent.of(header.copy(title = "Before"), base.content.blocks, false), base.change)

        assertThatThrownBy { service.changeHeader(context(ownerAccess), documentId, HeaderChange("New", null, null, null, null, 2)) }
            .isInstanceOf(StaleRevisionException::class.java)
            .hasMessage("the change was based on revision 2 but the document is at revision 3")
    }

    @Test
    fun `a header change on an older revision goes through when only other things changed since`() {
        every { documents.findByIdForUpdate(documentId) } returns current
        every { revisions.find(documentId, 2) } returns revision(2, listOf(first), byAgent, now.minusSeconds(600))
        every { documents.update(any(), any(), false) } answers { firstArg() }

        val agentContext = context(agentAccess(Permission.EDIT, setOf(spaceId)), ChangeSource.MCP)
        val written = service.changeHeader(agentContext, documentId, HeaderChange("New", null, null, null, null, 2))

        assertThat(written.document.header.title).isEqualTo("New")
        assertThat(written.revision).isEqualTo(4)
    }

    @Test
    fun `a restore based on an older revision is refused`() {
        every { documents.findByIdForUpdate(documentId) } returns current

        assertThatThrownBy { service.restore(context(ownerAccess), documentId, 1, 2) }
            .isInstanceOf(StaleRevisionException::class.java)
    }

    @Test
    fun `moving blocks is a change even though no block changed`() {
        every { documents.findByIdForUpdate(documentId) } returns current
        every { documents.update(any(), any(), false) } answers { firstArg() }

        val written =
            service.save(
                context(agentAccess(Permission.EDIT, setOf(spaceId)), ChangeSource.MCP),
                documentId,
                3,
                listOf(second, first),
            )

        assertThat(written.unchanged).isFalse()
        assertThat(written.document.blocks).containsExactly(second, first)
        assertThat(written.changedBlocks).isEmpty()
    }

    @Test
    fun `a retry from a connection that lost the space finds nothing`() {
        val key = OperationKey(WorkspaceFixtures.connectionId.toString(), "op-1")
        every { operations.find(key) } returns OperationRecord("document.edit", documentId.value, 7)
        every { documents.findById(documentId) } returns document(listOf(first), revision = 9)

        val narrowed = context(agentAccess(Permission.EDIT, emptySet()), ChangeSource.MCP, "op-1")

        assertThatThrownBy { service.edit(narrowed, documentId, emptyList(), null) }.isInstanceOf(DocumentNotFoundException::class.java)
    }
}
