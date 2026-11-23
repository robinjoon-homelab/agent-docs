package xyz.robinjoon.agentdocs.workspace.application

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.application.port.output.ActivityReporter
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.agent
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.agentAccess
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.context
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.document
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.documentId
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.now
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.owner
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.ownerAccess
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.paragraph
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.revision
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.space
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.spaceId
import xyz.robinjoon.agentdocs.workspace.application.model.DesignValidationWarning
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentDraft
import xyz.robinjoon.agentdocs.workspace.application.model.HeaderChange
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.OperationKey
import xyz.robinjoon.agentdocs.workspace.application.model.OperationRecord
import xyz.robinjoon.agentdocs.workspace.application.model.OperationResultState
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.input.LegacyOperationException
import xyz.robinjoon.agentdocs.workspace.application.port.input.PermissionDeniedException
import xyz.robinjoon.agentdocs.workspace.application.port.input.ResultRemovedException
import xyz.robinjoon.agentdocs.workspace.application.port.input.StaleRevisionException
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.FlowDesignValidator
import xyz.robinjoon.agentdocs.workspace.application.port.output.OperationStore
import xyz.robinjoon.agentdocs.workspace.application.port.output.RevisionCatalog
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.ThreadRepository
import xyz.robinjoon.agentdocs.workspace.application.service.DocumentService
import xyz.robinjoon.agentdocs.workspace.application.service.WriteJournal
import xyz.robinjoon.agentdocs.workspace.domain.Permission
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.ChangeSource
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentProperties
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentStatus
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowDesignBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowWarning
import xyz.robinjoon.agentdocs.workspace.domain.document.InvalidDocumentException
import xyz.robinjoon.agentdocs.workspace.domain.document.PathTakenException
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.EditConflictException
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.EditOperation
import xyz.robinjoon.agentdocs.workspace.domain.document.history.ChangeOrigin
import xyz.robinjoon.agentdocs.workspace.domain.document.history.RevisionContent
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown
import java.util.UUID

// Every port is a strict mock: a call a test does not prepare fails the test.
class DocumentServiceTest {
    private val documents = mockk<DocumentRepository>()
    private val revisions = mockk<RevisionCatalog>()
    private val spaces = mockk<SpaceRepository>()
    private val threads = mockk<ThreadRepository>()
    private val activity = mockk<ActivityReporter>()
    private val operations = mockk<OperationStore>()
    private val designs = mockk<FlowDesignValidator>()
    private val service =
        DocumentService(documents, revisions, spaces, threads, WriteJournal(activity, operations, WorkspaceFixtures.clock), designs)
    private val first = paragraph("first1", "First")
    private val second = paragraph("second", "Second")
    private val current = document(listOf(first, second), revision = 3)
    private val byAgent = ChangeOrigin(agent, ChangeSource.MCP)
    private val byOwnerOnWeb = ChangeOrigin(owner, ChangeSource.WEB)

    init {
        every { activity.record(any()) } just runs
        every { spaces.findById(any()) } returns space
        every { revisions.marks(any()) } returns emptyList()
        every { documents.pending(any()) } returns null
        every { documents.confirmPending(any()) } answers { firstArg() }
        every { documents.savePending(any(), any()) } answers { firstArg() }
    }

    @Test
    fun `a successful design write returns the original server block ID for a model warning`() {
        val path = DocumentPath("design")
        val config =
            FlowDesignBlock.parse(
                BlockId("outerconfig"),
                """{"id":"settings","kind":"settings","modelVersion":4,"frame":"phone","idPrefix":"M","nextScreen":2}""",
            )
        val screen =
            FlowDesignBlock.parse(
                BlockId("outerview"),
                """{"id":"memo","kind":"screen","code":"M-01","name":"메모","root":{"id":"root","kind":"box","layout":{"mode":"free"},
                    "children":[],"style":{"fill":{"token":"accent"}}}}""",
            )
        every { spaces.findBySlug(space.slug) } returns space
        every { documents.findByPath(spaceId, path) } returns null
        every { documents.add(any(), any()) } answers { firstArg() }
        every { designs.validate(any(), null) } returns listOf(DesignValidationWarning("memo", "missingToken", "accent"))
        val draft = DocumentDraft(path, "메모", DocumentProperties.initial, listOf(config, screen), "flow")
        val written = service.create(context(ownerAccess), space.slug, draft)
        assertThat(written.designWarnings).containsExactly(FlowWarning(screen.id, "missingToken", "accent"))
    }

    @Test
    fun `a rejected design batch never writes a document or pending revision`() {
        val settings = FlowDesignBlock.parse(BlockId("config"), """{"kind":"settings","id":"config","modelVersion":4,"nextScreen":2}""")
        val screen =
            FlowDesignBlock.parse(
                BlockId("screen"),
                """{"kind":"screen","id":"memo","code":"M-01","name":"Memo","frame":"note"}""",
            )
        val flow = Document.create(documentId, current.header.copy(type = "flow"), listOf(settings, screen), now)
        val next = listOf(settings, screen.withFields(screen.fields + ("invalid" to true)))
        every { documents.findByIdForUpdate(documentId) } returns flow
        every {
            designs.validate(
                FlowDesignBlock.documentJson(documentId, next),
                FlowDesignBlock.documentJson(documentId, listOf(settings, screen)),
            )
        } throws InvalidDocumentException("invalid design field")
        assertThatThrownBy { service.save(context(ownerAccess), documentId, flow.version, next) }
            .isInstanceOf(InvalidDocumentException::class.java)
            .hasMessageContaining("invalid design field")
        verify(exactly = 0) { documents.savePending(any(), any()) }
        verify(exactly = 0) { documents.update(any(), any(), any()) }
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
    fun `a human web save increments version while keeping the confirmed revision`() {
        every { documents.findByIdForUpdate(documentId) } returns current
        every { revisions.find(documentId, 3) } returns revision(3, current.blocks, byAgent, now.minusSeconds(5))

        val written = service.save(context(ownerAccess), documentId, 3, listOf(first, paragraph("second", "Second, edited")))

        assertThat(written.revision).isEqualTo(3)
        assertThat(written.document.version).isEqualTo(4)
        assertThat(written.amended).isFalse()
        assertThat(written.merged).isFalse()
        assertThat(written.changedBlocks).containsExactly(second.id)
    }

    @Test
    fun `a person's continued web typing updates the pending revision`() {
        val typed = current.amend(current.header, current.blocks, now.minusSeconds(20))
        every { documents.findByIdForUpdate(documentId) } returns typed
        every { documents.pending(documentId) } returns revision(4, typed.blocks, byOwnerOnWeb, now.minusSeconds(20))
        every { revisions.find(documentId, 3) } returns revision(3, current.blocks, byAgent, now.minusSeconds(600))

        val written = service.save(context(ownerAccess), documentId, 4, listOf(first, paragraph("second", "Second, typed on")))

        assertThat(written.revision).isEqualTo(3)
        assertThat(written.amended).isTrue()
        assertThat(written.document.version).isEqualTo(5)
        assertThat(written.document.blocks.last()).isEqualTo(paragraph("second", "Second, typed on"))
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
    fun `a human REST save creates a confirmed revision`() {
        every { documents.findByIdForUpdate(documentId) } returns current
        every { documents.update(any(), any(), false) } answers { firstArg() }

        val written = service.save(context(ownerAccess, ChangeSource.REST), documentId, 3, listOf(first))

        assertThat(written.revision).isEqualTo(4)
        assertThat(written.document.version).isEqualTo(4)
        assertThat(written.amended).isFalse()
    }

    @Test
    fun `metadata changes by a person on WEB share the pending revision`() {
        every { documents.findByIdForUpdate(documentId) } returns current
        every { documents.pending(documentId) } returns revision(4, current.blocks, byOwnerOnWeb, now.minusSeconds(10))
        every { revisions.find(documentId, 3) } returns revision(3, current.blocks, byAgent, now.minusSeconds(600))

        val written = service.changeHeader(context(ownerAccess), documentId, HeaderChange("New", null, null, null, null, 3))

        assertThat(written.document.header.title).isEqualTo("New")
        assertThat(written.document.version).isEqualTo(4)
        assertThat(written.revision).isEqualTo(3)
        assertThat(written.amended).isTrue()
    }

    @Test
    fun `explicit finalize returns the confirmed revision without changing content version`() {
        val typed = current.amend(current.header, listOf(first), now.minusSeconds(10))
        val confirmed = typed.confirmRevision()
        every { documents.findByIdForUpdate(documentId) } returns typed
        every { documents.confirmPending(typed) } returns confirmed
        every { documents.pending(documentId) } returns revision(4, typed.blocks, byOwnerOnWeb, now).copy(version = typed.version)

        val written = service.finalizeEdits(context(ownerAccess), documentId, typed.version)

        assertThat(written.document).isEqualTo(confirmed)
        assertThat(written.revision).isEqualTo(4)
        assertThat(written.document.version).isEqualTo(4)
        assertThat(written.document.blocks).isEqualTo(typed.blocks)
    }

    @Test
    fun `an explicit finalize cannot confirm a different authors pending input`() {
        val typed = current.amend(current.header, listOf(first), now)
        every { documents.findByIdForUpdate(documentId) } returns typed
        every { documents.pending(documentId) } returns revision(4, typed.blocks, byAgent, now).copy(version = typed.version)
        every { documents.confirmPending(typed) } returns typed.confirmRevision()

        val written = service.finalizeEdits(context(ownerAccess), documentId, typed.version)

        assertThat(written.document).isEqualTo(typed)
        assertThat(written.revision).isEqualTo(3)
    }

    @Test
    fun `an older tab cannot confirm the same authors newer pending version`() {
        val typed = current.amend(current.header, listOf(first), now)
        every { documents.findByIdForUpdate(documentId) } returns typed
        every { documents.pending(documentId) } returns revision(4, typed.blocks, byOwnerOnWeb, now).copy(version = typed.version)
        every { documents.confirmPending(typed) } returns typed.confirmRevision()

        val written = service.finalizeEdits(context(ownerAccess), documentId, typed.version - 1)

        assertThat(written.document).isEqualTo(typed)
        assertThat(written.revision).isEqualTo(3)
    }

    @Test
    fun `a restore without baseVersion is rejected`() {
        every { documents.findByIdForUpdate(documentId) } returns current

        assertThatThrownBy { service.restore(context(ownerAccess), documentId, 1, null) }
            .isInstanceOf(InvalidRequestException::class.java)
            .hasMessage("baseVersion is required")
    }

    @Test
    fun `any stale content save is rejected with the full current document even for disjoint changes`() {
        val changedByOthers = document(listOf(paragraph("first1", "First, by the agent"), second), revision = 3)
        every { documents.findByIdForUpdate(documentId) } returns changedByOthers

        assertThatThrownBy { service.save(context(ownerAccess), documentId, 2, listOf(first, paragraph("second", "Second, by me"))) }
            .isInstanceOfSatisfying(StaleRevisionException::class.java) {
                assertThat(it.document).isEqualTo(changedByOthers)
            }
    }

    @Test
    fun `a pending change makes an old version stale even while the confirmed revision stays the same`() {
        val typed = current.amend(current.header, listOf(first), now.minusSeconds(10))
        every { documents.findByIdForUpdate(documentId) } returns typed

        assertThatThrownBy { service.save(context(ownerAccess), documentId, 3, typed.blocks) }
            .isInstanceOfSatisfying(StaleRevisionException::class.java) {
                assertThat(it.document?.revision).isEqualTo(3)
                assertThat(it.document?.version).isEqualTo(4)
                assertThat(it.document?.blocks).containsExactly(first)
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
    fun `a future baseVersion is rejected as a version conflict`() {
        every { documents.findByIdForUpdate(documentId) } returns current

        assertThatThrownBy { service.save(context(ownerAccess), documentId, 9, current.blocks) }
            .isInstanceOf(StaleRevisionException::class.java)
            .hasMessage("the change was based on version 9 but the document is at version 3")
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
        val warning = FlowWarning(first.id, "orphanedReference", "original target")
        val snapshot =
            xyz.robinjoon.agentdocs.workspace.application.model.DocumentWrite(
                document(listOf(first), revision = 7),
                7,
                listOf(first.id),
                amended = false,
                unchanged = false,
                designWarnings = listOf(warning),
            )
        every { operations.findForWrite(key) } returns OperationRecord("document.edit", documentId.value, 7, documentWrite = snapshot)
        every { documents.findById(documentId) } returns document(listOf(first), revision = 9)
        every { revisions.find(documentId, 7) } returns revision(7, listOf(first), byAgent, now.minusSeconds(30))

        val agentContext = context(agentAccess(Permission.EDIT, setOf(spaceId)), ChangeSource.MCP, "op-1")
        val written = service.edit(agentContext, documentId, listOf(EditOperation.Delete(first.id, BlockMarkdown.hash(first))), null)

        assertThat(written.revision).isEqualTo(7)
        assertThat(written.changedBlocks).containsExactly(first.id)
        assertThat(written.designWarnings).containsExactly(warning)
    }

    @Test
    fun `an operation id reused for another kind of write is rejected`() {
        val key = OperationKey(WorkspaceFixtures.connectionId.toString(), "op-1")
        every { operations.findForWrite(key) } returns OperationRecord("thread.open", documentId.value, null)

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
        every { operations.findForWrite(OperationKey(WorkspaceFixtures.ownerId.toString(), "op-trash")) } returns
            OperationRecord(
                "document.trash",
                documentId.value,
                4,
                documentWrite =
                    xyz.robinjoon.agentdocs.workspace.application.model.DocumentWrite(
                        document(listOf(first), revision = 4, trashedAt = now.minusSeconds(5)),
                        4,
                        emptyList(),
                        amended = false,
                        unchanged = false,
                    ),
            )
        every { documents.findById(documentId) } returns document(listOf(first), revision = 4, trashedAt = now.minusSeconds(5))
        every { revisions.find(documentId, 4) } returns revision(4, listOf(first), byOwnerOnWeb, now.minusSeconds(5))

        val written = service.trash(context(ownerAccess, operationId = "op-trash"), documentId)

        assertThat(written.revision).isEqualTo(4)
        assertThat(written.unchanged).isFalse()
    }

    @Test
    fun `a retried permanent delete finds the document already gone and succeeds`() {
        every { operations.findForWrite(OperationKey(WorkspaceFixtures.ownerId.toString(), "op-purge")) } returns
            OperationRecord("document.purge", documentId.value, null, state = OperationResultState.COMPLETED)
        every { documents.findByIdForUpdate(documentId) } returns null

        assertThatCode { service.purge(context(ownerAccess, operationId = "op-purge"), documentId) }.doesNotThrowAnyException()
    }

    @Test
    fun `expiry counts only old documents rather than recently trashed descendants under a reused path`() {
        val expired = document(listOf(first), 3, trashedAt = now.minusSeconds(31 * 86_400L))
        val recent = document(listOf(second), 3, trashedAt = now.minusSeconds(86_400))
        val child =
            Document.restore(
                DocumentId(UUID.randomUUID()),
                recent.header.copy(
                    path =
                        DocumentPath("${expired.header.path.value}/new-child"),
                ),
                recent.blocks,
                recent.lifecycle,
            )
        every { documents.trashedBefore(now.minusSeconds(30 * 86_400L)) } returns listOf(expired.id)
        every { documents.findByIdForUpdate(expired.id) } returns expired
        every { documents.lockTrashedUnder(spaceId, expired.header.path) } returns listOf(child)
        every { documents.delete(any()) } just runs
        every { operations.removeDocumentResults(any()) } just runs

        val deleted = service.purgeExpiredTrash()

        assertThat(deleted).isEqualTo(1)
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
    fun `restoring writes historical content and metadata at the current path and space`() {
        val historicalHeader =
            current.header.copy(
                spaceId = SpaceId(UUID.randomUUID()),
                path = DocumentPath("removed-parent/earlier-path"),
                title = "Historical title",
                properties = DocumentProperties.of(listOf("old-tag"), DocumentStatus.IN_REVIEW, "octocat"),
            )
        val historical =
            revision(1, listOf(first), byOwnerOnWeb, now.minusSeconds(3_600))
                .copy(content = RevisionContent.of(historicalHeader, listOf(first), trashed = true))
        every { documents.findByIdForUpdate(documentId) } returns current
        every { revisions.find(documentId, 1) } returns historical
        every { documents.update(any(), any(), false) } answers { firstArg() }

        val written = service.restore(context(ownerAccess), documentId, 1, 3)

        assertThat(written.revision).isEqualTo(4)
        assertThat(written.document.version).isEqualTo(4)
        assertThat(written.document.blocks).containsExactly(first)
        assertThat(written.document.header).isEqualTo(historicalHeader.copy(spaceId = current.spaceId, path = current.header.path))
        assertThat(written.document.isTrashed).isFalse()
    }

    @Test
    fun `a header change on an older revision is refused when someone changed that field since`() {
        every { documents.findByIdForUpdate(documentId) } returns current

        assertThatThrownBy { service.changeHeader(context(ownerAccess), documentId, HeaderChange("New", null, null, null, null, 2)) }
            .isInstanceOf(StaleRevisionException::class.java)
            .hasMessage("the change was based on version 2 but the document is at version 3")
    }

    @Test
    fun `a stale header is rejected even when only the body changed`() {
        every { documents.findByIdForUpdate(documentId) } returns current

        assertThatThrownBy { service.changeHeader(context(ownerAccess), documentId, HeaderChange("New", null, null, null, null, 2)) }
            .isInstanceOfSatisfying(StaleRevisionException::class.java) {
                assertThat(it.document).isEqualTo(current)
            }
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
        assertThat(written.changedBlocks).containsExactly(second.id, first.id)
    }

    @Test
    fun `a removed response never reapplies the successful write or exposes a new result`() {
        every { operations.findForWrite(any()) } returns
            OperationRecord("document.save", documentId.value, 2, state = OperationResultState.REMOVED)
        every { documents.findById(documentId) } returns current

        repeat(2) {
            assertThatThrownBy { service.save(context(ownerAccess, operationId = "removed"), documentId, 1, emptyList()) }
                .isInstanceOf(ResultRemovedException::class.java)
                .hasMessageContaining("already succeeded")
        }
        assertThat(current.version).isEqualTo(3)
    }

    @Test
    fun `a purged root reports removed only to a prior writer still allowed to edit its last space`() {
        val key = OperationKey(WorkspaceFixtures.connectionId.toString(), "purged")
        every { operations.findForWrite(key) } returns
            OperationRecord("document.save", documentId.value, 2, state = OperationResultState.REMOVED, purgedFrom = spaceId)
        every { documents.findById(documentId) } returns null
        val stillAuthorized = context(agentAccess(Permission.EDIT, setOf(spaceId)), ChangeSource.MCP, "purged")
        val lostSpace = context(agentAccess(Permission.EDIT, emptySet()), ChangeSource.MCP, "purged")
        val readOnly = context(agentAccess(Permission.READ, setOf(spaceId)), ChangeSource.MCP, "purged")

        assertThatThrownBy { service.save(stillAuthorized, documentId, 1, emptyList()) }.isInstanceOf(ResultRemovedException::class.java)
        assertThatThrownBy { service.save(lostSpace, documentId, 1, emptyList()) }.isInstanceOf(DocumentNotFoundException::class.java)
        assertThatThrownBy { service.save(readOnly, documentId, 1, emptyList()) }.isInstanceOf(PermissionDeniedException::class.java)
    }

    @Test
    fun `legacy document keys never reapply a successful write`() {
        val key = OperationKey(WorkspaceFixtures.ownerId.toString(), "legacy")
        every { operations.findForWrite(key) } returns OperationRecord("document.save", documentId.value, 2)
        every { documents.findById(documentId) } returns current

        repeat(2) {
            assertThatThrownBy { service.save(context(ownerAccess, operationId = "legacy"), documentId, 1, emptyList()) }
                .isInstanceOf(LegacyOperationException::class.java)
                .hasMessageContaining("already succeeded")
        }
        assertThat(current.version).isEqualTo(3)
        assertThat(current.blocks).containsExactly(first, second)
    }

    @Test
    fun `a retry from a connection that lost the space finds nothing`() {
        val key = OperationKey(WorkspaceFixtures.connectionId.toString(), "op-1")
        every { operations.findForWrite(key) } returns OperationRecord("document.edit", documentId.value, 7)
        every { documents.findById(documentId) } returns document(listOf(first), revision = 9)

        val narrowed = context(agentAccess(Permission.EDIT, emptySet()), ChangeSource.MCP, "op-1")

        assertThatThrownBy { service.edit(narrowed, documentId, emptyList(), null) }.isInstanceOf(DocumentNotFoundException::class.java)
    }
}
