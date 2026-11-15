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
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.otherSpaceId
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.ownerAccess
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.spaceId
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentTransfer
import xyz.robinjoon.agentdocs.workspace.application.port.input.SpaceNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.input.StaleRevisionException
import xyz.robinjoon.agentdocs.workspace.application.port.output.AssetRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentAssetStore
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.InboxRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.OperationStore
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.ThreadRepository
import xyz.robinjoon.agentdocs.workspace.application.service.DocumentTransferAssets
import xyz.robinjoon.agentdocs.workspace.application.service.DocumentTransferService
import xyz.robinjoon.agentdocs.workspace.application.service.WriteJournal
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath
import xyz.robinjoon.agentdocs.workspace.domain.document.PathTakenException
import xyz.robinjoon.agentdocs.workspace.domain.space.Space
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceProfile
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import java.util.UUID

class DocumentTransferServiceTest {
    private val documents = mockk<DocumentRepository>()
    private val spaces = mockk<SpaceRepository>()
    private val threads = mockk<ThreadRepository>()
    private val inbox = mockk<InboxRepository>()
    private val references = mockk<DocumentAssetStore>()
    private val activity = mockk<ActivityReporter>()
    private val service =
        DocumentTransferService(
            documents,
            spaces,
            threads,
            inbox,
            DocumentTransferAssets(
                mockk<AssetRepository>(),
                references,
            ),
            WriteJournal(
                activity,
                mockk<OperationStore>(),
                WorkspaceFixtures.clock,
            ),
        )
    private val current = document(listOf(WorkspaceFixtures.paragraph("block1", "Original")), 3)
    private val child =
        Document.restore(
            DocumentId(UUID.randomUUID()),
            current.header.copy(path = DocumentPath("payments/retry/child")),
            current.blocks,
            current.lifecycle,
        )
    private val target = Space.create(otherSpaceId, SpaceSlug("archive"), SpaceProfile.of("Archive", "", emptyList()), now)
    private val request = DocumentTransfer(target.slug, DocumentPath("moved"), 3)

    init {
        every { documents.findByIdForUpdate(documentId) } returns current
        every { spaces.findBySlug(target.slug) } returns target
        every { documents.lockLiveUnder(spaceId, current.header.path) } returns listOf(child)
        every { documents.findByPath(otherSpaceId, any()) } returns null
        every { documents.pending(any()) } returns null
        every { documents.confirmPending(any()) } answers { firstArg() }
        every { documents.update(any(), any(), false) } answers { firstArg() }
        every { threads.lockByDocuments(any()) } returns emptyList()
        every { threads.moveDocuments(any(), otherSpaceId) } just runs
        every { inbox.moveDocuments(any(), otherSpaceId) } just runs
        every { references.bindings(any()) } returns emptyList()
        every { references.references(any()) } returns emptySet()
        every { references.sharedOutside(emptySet(), any()) } returns emptySet()
        every { references.bind(emptyList()) } just runs
        every { activity.record(any()) } just runs
    }

    @Test
    fun `root and live descendants move together retaining ids content and independent revision numbers`() {
        val written = service.transfer(context(ownerAccess), documentId, request)
        assertThat(written.document.id).isEqualTo(current.id)
        assertThat(written.document.spaceId).isEqualTo(otherSpaceId)
        assertThat(written.document.header.path).isEqualTo(DocumentPath("moved"))
        assertThat(written.document.blocks).isEqualTo(current.blocks)
        assertThat(written.document.version).isEqualTo(4)
        assertThat(
            written.related
                .single()
                .document.id,
        ).isEqualTo(child.id)
        assertThat(
            written.related
                .single()
                .document.header.path,
        ).isEqualTo(DocumentPath("moved/child"))
        assertThat(
            written.related
                .single()
                .document.spaceId,
        ).isEqualTo(otherSpaceId)
        assertThat(written.space?.slug).isEqualTo(target.slug)
    }

    @Test
    fun `a stale version and a missing destination refuse transfer`() {
        assertThatThrownBy { service.transfer(context(ownerAccess), documentId, request.copy(baseVersion = 2)) }
            .isInstanceOf(StaleRevisionException::class.java)
        every { spaces.findBySlug(target.slug) } returns null
        assertThatThrownBy { service.transfer(context(ownerAccess), documentId, request) }.isInstanceOf(SpaceNotFoundException::class.java)
    }

    @Test
    fun `any child destination collision refuses the complete tree`() {
        every {
            documents.findByPath(
                otherSpaceId,
                DocumentPath("moved/child"),
            )
        } returns
            Document.create(
                DocumentId(UUID.randomUUID()),
                child.header,
                emptyList(),
                now,
            )
        assertThatThrownBy { service.transfer(context(ownerAccess), documentId, request) }.isInstanceOf(PathTakenException::class.java)
    }
}
