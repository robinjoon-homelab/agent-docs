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
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.now
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.owner
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.ownerAccess
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.paragraph
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.space
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.spaceId
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentDraft
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentLocation
import xyz.robinjoon.agentdocs.workspace.application.model.HeaderChange
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.RelatedChange
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.OperationStore
import xyz.robinjoon.agentdocs.workspace.application.port.output.RevisionCatalog
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.ThreadRepository
import xyz.robinjoon.agentdocs.workspace.application.service.DocumentService
import xyz.robinjoon.agentdocs.workspace.application.service.WriteJournal
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentHeader
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentLifecycle
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentProperties
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentStatus
import xyz.robinjoon.agentdocs.workspace.domain.document.PathTakenException
import java.time.Instant
import java.util.UUID

// Parent documents come from paths: writes fill in missing parents and take descendants along. Every port is a
// strict mock, so a test that does not accept writes fails if anything is written.
class DocumentTreeTest {
    private val documents = mockk<DocumentRepository>()
    private val revisions = mockk<RevisionCatalog>()
    private val spaces = mockk<SpaceRepository>()
    private val threads = mockk<ThreadRepository>()
    private val activity = mockk<ActivityReporter>()
    private val operations = mockk<OperationStore>()
    private val service =
        DocumentService(documents, revisions, spaces, threads, WriteJournal(activity, operations, WorkspaceFixtures.clock))

    init {
        every { activity.record(any()) } just runs
        every { revisions.find(any(), any()) } returns null
    }

    @Test
    fun `creating under a missing parent creates an empty parent named after its segment`() {
        acceptWrites()
        every { spaces.findBySlug(space.slug) } returns space
        every { documents.findByPath(spaceId, path("기획/결제/재시도")) } returns null
        every { documents.lockLiveAt(spaceId, listOf(path("기획"), path("기획/결제"))) } returns listOf(live("기획"))

        val draft = DocumentDraft(path("기획/결제/재시도"), "재시도", DocumentProperties.initial, listOf(paragraph("intro1", "Why")))
        val written = service.create(context(ownerAccess), space.slug, draft)

        val parent = written.related.single()
        assertThat(parent.change).isEqualTo(RelatedChange.CREATED)
        assertThat(parent.document.header.path).isEqualTo(path("기획/결제"))
        assertThat(parent.document.header.title).isEqualTo("결제")
        assertThat(parent.document.header.properties.status).isEqualTo(DocumentStatus.DRAFT)
        assertThat(parent.document.blocks).isEmpty()
    }

    @Test
    fun `moving a document takes its descendants along and keeps their ids`() {
        acceptWrites()
        val moving = live("payments")
        val child = live("payments/retry")
        val grandchild = live("payments/retry/backoff")
        every { documents.findByIdForUpdate(moving.id) } returns moving
        every { documents.lockLiveUnder(spaceId, path("payments")) } returns listOf(child, grandchild)
        listOf("billing/old", "billing/old/retry", "billing/old/retry/backoff").forEach {
            every { documents.findByPath(spaceId, path(it)) } returns null
        }
        every { documents.lockLiveAt(spaceId, listOf(path("billing"))) } returns listOf(live("billing"))

        val written = service.changeHeader(context(ownerAccess), moving.id, pathChange("billing/old"))

        assertThat(written.document.header.path).isEqualTo(path("billing/old"))
        assertThat(written.related.map { it.change }).containsOnly(RelatedChange.MOVED)
        assertThat(written.related.map { it.document.id to it.document.header.path }).containsExactly(
            child.id to path("billing/old/retry"),
            grandchild.id to path("billing/old/retry/backoff"),
        )
    }

    @Test
    fun `a document cannot move under itself`() {
        val moving = live("payments")
        every { documents.findByIdForUpdate(moving.id) } returns moving

        assertThatThrownBy { service.changeHeader(context(ownerAccess), moving.id, pathChange("payments/inner")) }
            .isInstanceOf(InvalidRequestException::class.java)
            .hasMessage("payments cannot move under itself")
    }

    @Test
    fun `a document in the trash cannot move, since the documents now under its old path are not its own`() {
        val inTrash = trashed("payments", now.minusSeconds(600))
        every { documents.findByIdForUpdate(inTrash.id) } returns inTrash

        assertThatThrownBy { service.changeHeader(context(ownerAccess), inTrash.id, pathChange("billing")) }
            .isInstanceOf(InvalidRequestException::class.java)
            .hasMessage("a document in the trash cannot move")
    }

    @Test
    fun `a descendant's target path in use stops the whole move before anything is written and names that path`() {
        val moving = live("payments")
        every { documents.findByIdForUpdate(moving.id) } returns moving
        every { documents.lockLiveUnder(spaceId, path("payments")) } returns listOf(live("payments/retry"))
        every { documents.findByPath(spaceId, path("billing")) } returns null
        every { documents.findByPath(spaceId, path("billing/retry")) } returns live("billing/retry")

        assertThatThrownBy { service.changeHeader(context(ownerAccess), moving.id, pathChange("billing")) }
            .isInstanceOf(PathTakenException::class.java)
            .hasMessageContaining("billing/retry")
    }

    @Test
    fun `trashing a document puts its descendants in the trash at the same moment`() {
        acceptWrites()
        val trashing = live("payments")
        every { documents.findByIdForUpdate(trashing.id) } returns trashing
        every { documents.lockLiveUnder(spaceId, path("payments")) } returns listOf(live("payments/retry"))

        val written = service.trash(context(ownerAccess), trashing.id)

        val descendant = written.related.single()
        assertThat(descendant.change).isEqualTo(RelatedChange.TRASHED)
        assertThat(descendant.document.lifecycle.trashedAt).isEqualTo(written.document.lifecycle.trashedAt)
    }

    @Test
    fun `restoring brings back the documents trashed together with it`() {
        acceptWrites()
        val trashedAt = now.minusSeconds(600)
        val restoring = trashed("payments", trashedAt)
        val child = trashed("payments/retry", trashedAt)
        every { documents.findByIdForUpdate(restoring.id) } returns restoring
        every { documents.lockTrashedTogether(spaceId, path("payments"), trashedAt) } returns listOf(child)
        every { documents.findByPath(spaceId, path("payments")) } returns null
        every { documents.findByPath(spaceId, path("payments/retry")) } returns null

        val written = service.untrash(context(ownerAccess), restoring.id)

        val descendant = written.related.single()
        assertThat(written.document.isTrashed).isFalse()
        assertThat(descendant.document.id to descendant.change).isEqualTo(child.id to RelatedChange.RESTORED)
        assertThat(descendant.document.isTrashed).isFalse()
    }

    @Test
    fun `a restore whose descendant cannot take its path back restores nothing`() {
        val trashedAt = now.minusSeconds(600)
        val restoring = trashed("payments", trashedAt)
        every { documents.findByIdForUpdate(restoring.id) } returns restoring
        every { documents.lockTrashedTogether(spaceId, path("payments"), trashedAt) } returns listOf(trashed("payments/retry", trashedAt))
        every { documents.findByPath(spaceId, path("payments")) } returns null
        every { documents.findByPath(spaceId, path("payments/retry")) } returns live("payments/retry").copyWithNewId()

        assertThatThrownBy { service.untrash(context(ownerAccess), restoring.id) }
            .isInstanceOf(PathTakenException::class.java)
            .hasMessageContaining("payments/retry")
    }

    @Test
    fun `restoring a child brings back its trashed parent and creates a parent that is gone`() {
        acceptWrites()
        val restoring = trashed("a/b/c", now.minusSeconds(60))
        val parentInTrash = trashed("a/b", now.minusSeconds(600))
        every { documents.findByIdForUpdate(restoring.id) } returns restoring
        every { documents.lockTrashedTogether(spaceId, path("a/b/c"), now.minusSeconds(60)) } returns emptyList()
        every { documents.findByPath(spaceId, path("a/b/c")) } returns null
        every { documents.lockLiveAt(spaceId, listOf(path("a"), path("a/b"))) } returns emptyList()
        every { documents.lockLastTrashedAt(spaceId, path("a")) } returns null
        every { documents.lockLastTrashedAt(spaceId, path("a/b")) } returns parentInTrash

        val written = service.untrash(context(ownerAccess), restoring.id)

        assertThat(written.related.map { it.document.header.path to it.change }).containsExactly(
            path("a") to RelatedChange.CREATED,
            path("a/b") to RelatedChange.RESTORED,
        )
        assertThat(
            written.related
                .last()
                .document.id,
        ).isEqualTo(parentInTrash.id)
    }

    @Test
    fun `filling in parents gives every folder path without a document an empty one named after it`() {
        acceptWrites()
        every { documents.livePaths() } returns
            listOf(location("기획/기능-정의서"), location("기획/디자인/화면"), location("기획/디자인"), location("메모"))
        every { revisions.authorOf("robin") } returns owner
        every { documents.lockLiveAt(spaceId, listOf(path("기획"))) } returns emptyList()
        every { documents.lockLiveUnder(spaceId, path("기획")) } returns listOf(live("기획/기능-정의서"))

        val created = service.fillMissingParents("robin").single()

        assertThat(created.header.path).isEqualTo(path("기획"))
        assertThat(created.header.title).isEqualTo("기획")
        assertThat(created.header.properties.status).isEqualTo(DocumentStatus.DRAFT)
        assertThat(created.blocks).isEmpty()
    }

    @Test
    fun `a folder whose documents went to the trash after the paths were read gets no document`() {
        every { documents.livePaths() } returns listOf(location("a"), location("a/b/c"))
        every { revisions.authorOf("robin") } returns owner
        every { documents.lockLiveAt(spaceId, listOf(path("a"), path("a/b"))) } returns emptyList()
        every { documents.lockLiveUnder(spaceId, path("a/b")) } returns emptyList()

        assertThat(service.fillMissingParents("robin")).isEmpty()
    }

    @Test
    fun `filling in parents again finds nothing to add`() {
        every { documents.livePaths() } returns listOf(location("기획"), location("기획/기능-정의서"))

        assertThat(service.fillMissingParents("robin")).isEmpty()
    }

    private fun acceptWrites() {
        every { documents.add(any(), any()) } answers { firstArg() }
        every { documents.update(any(), any(), false) } answers { firstArg() }
    }

    private fun path(value: String) = DocumentPath(value)

    private fun location(value: String) = DocumentLocation(spaceId, path(value))

    private fun pathChange(to: String) = HeaderChange(null, path(to), null, null, null, 2)

    private fun live(value: String): Document = stored(value, null)

    private fun trashed(
        value: String,
        at: Instant,
    ): Document = stored(value, at)

    // Another document at the same path, such as one created there after the first went to the trash.
    private fun Document.copyWithNewId(): Document = Document.restore(DocumentId(UUID.randomUUID()), header, blocks, lifecycle)

    private fun stored(
        value: String,
        trashedAt: Instant?,
    ): Document =
        Document.restore(
            DocumentId(UUID.nameUUIDFromBytes(value.toByteArray())),
            DocumentHeader(spaceId, path(value), path(value).name, DocumentProperties.initial),
            emptyList(),
            DocumentLifecycle(2, now.minusSeconds(3_600), now.minusSeconds(3_600), trashedAt),
        )
}
