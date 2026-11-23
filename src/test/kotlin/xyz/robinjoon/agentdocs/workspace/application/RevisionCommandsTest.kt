package xyz.robinjoon.agentdocs.workspace.application

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.application.port.output.ActivityReporter
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.context
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.document
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.documentId
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.now
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.owner
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.ownerAccess
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.paragraph
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.revision
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.space
import xyz.robinjoon.agentdocs.workspace.application.model.BlockRestore
import xyz.robinjoon.agentdocs.workspace.application.model.OperationKey
import xyz.robinjoon.agentdocs.workspace.application.model.OperationRecord
import xyz.robinjoon.agentdocs.workspace.application.model.OperationResultState
import xyz.robinjoon.agentdocs.workspace.application.model.RevisionLabel
import xyz.robinjoon.agentdocs.workspace.application.port.input.StaleRevisionException
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.OperationStore
import xyz.robinjoon.agentdocs.workspace.application.port.output.RevisionCatalog
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.ThreadRepository
import xyz.robinjoon.agentdocs.workspace.application.service.DocumentService
import xyz.robinjoon.agentdocs.workspace.application.service.WriteJournal
import xyz.robinjoon.agentdocs.workspace.domain.document.ChangeSource
import xyz.robinjoon.agentdocs.workspace.domain.document.history.ChangeOrigin

class RevisionCommandsTest {
    private val documents = mockk<DocumentRepository>()
    private val revisions = mockk<RevisionCatalog>()
    private val spaces = mockk<SpaceRepository>()
    private val operations = mockk<OperationStore>()
    private val activity = mockk<ActivityReporter>()
    private val service =
        DocumentService(
            documents,
            revisions,
            spaces,
            mockk<ThreadRepository>(),
            WriteJournal(activity, operations, WorkspaceFixtures.clock),
            mockk(),
        )
    private val old = paragraph("block1", "Old")
    private val unrelated = paragraph("other1", "Unrelated")
    private val current = document(listOf(paragraph("block1", "New"), unrelated), 3)

    init {
        every { activity.record(any()) } just runs
        every { documents.findByIdForUpdate(documentId) } returns current
        every { documents.pending(documentId) } returns null
        every { revisions.find(documentId, 1) } returns revision(1, listOf(old), ChangeOrigin(owner, ChangeSource.WEB), now)
        every { revisions.marks(documentId) } returns emptyList()
        every { documents.update(any(), any(), false) } answers { firstArg() }
        every { spaces.findById(any()) } returns space
    }

    @Test
    fun `one block restoration returns a new version preserving the header and unrelated block`() {
        val written = service.restoreBlock(context(ownerAccess), documentId, BlockRestore(1, old.id, 3))
        assertThat(written.document.blocks).containsExactly(old, unrelated)
        assertThat(written.document.header).isEqualTo(current.header)
        assertThat(written.document.version).isEqualTo(4)
        assertThat(written.revision).isEqualTo(4)
        assertThat(written.changedBlocks).containsExactly(old.id)
    }

    @Test
    fun `a stale block restore is refused before any content can be overwritten`() {
        assertThatThrownBy {
            service.restoreBlock(context(ownerAccess), documentId, BlockRestore(1, old.id, 2))
        }.isInstanceOf(StaleRevisionException::class.java)
    }

    @Test
    fun `an empty name removes the label without advancing document content`() {
        every { documents.nameRevision(documentId, 1, null) } just runs
        val label = service.nameRevision(context(ownerAccess), documentId, 1, "")
        assertThat(label).isEqualTo(RevisionLabel(documentId, 1, null))
        assertThat(current.version).isEqualTo(3)
    }

    @Test
    fun `a repeated naming request replays its original label after later changes`() {
        val key = OperationKey(owner.userId.toString(), "name-once")
        val original = RevisionLabel(documentId, 1, "Original")
        every { documents.findById(documentId) } returns current
        every { operations.findForWrite(key) } returns
            OperationRecord("revision.name", documentId.value, 1, state = OperationResultState.AVAILABLE, revisionLabel = original)
        assertThat(service.nameRevision(context(ownerAccess, operationId = "name-once"), documentId, 1, "Later")).isEqualTo(original)
    }

    @Test
    fun `restoring while typing first preserves pending content as a separate confirmed revision`() {
        val typed = current.amend(current.header, current.blocks, now)
        val confirmed = typed.confirmRevision()
        every { documents.findByIdForUpdate(documentId) } returns typed
        every { documents.pending(documentId) } returns
            revision(4, typed.blocks, ChangeOrigin(owner, ChangeSource.WEB), now).copy(version = typed.version)
        every { documents.confirmPending(typed) } returns confirmed
        val written = service.restoreBlock(context(ownerAccess), documentId, BlockRestore(1, old.id, typed.version))
        assertThat(written.revision).isEqualTo(5)
        assertThat(written.document.version).isEqualTo(5)
        assertThat(written.document.blocks).containsExactly(old, unrelated)
    }
}
