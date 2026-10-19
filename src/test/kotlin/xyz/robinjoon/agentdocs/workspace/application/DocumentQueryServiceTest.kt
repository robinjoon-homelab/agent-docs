package xyz.robinjoon.agentdocs.workspace.application

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.agentAccess
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.document
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.documentId
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.now
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.owner
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.ownerAccess
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.paragraph
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.revision
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.space
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.spaceId
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.MarkdownFormat
import xyz.robinjoon.agentdocs.workspace.application.model.MarkdownRequest
import xyz.robinjoon.agentdocs.workspace.application.model.RevisionFilter
import xyz.robinjoon.agentdocs.workspace.application.model.RevisionSummary
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentViewReporter
import xyz.robinjoon.agentdocs.workspace.application.port.output.RevisionCatalog
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.ThreadRepository
import xyz.robinjoon.agentdocs.workspace.application.service.DocumentQueryService
import xyz.robinjoon.agentdocs.workspace.domain.Permission
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.ChangeSource
import xyz.robinjoon.agentdocs.workspace.domain.document.Heading
import xyz.robinjoon.agentdocs.workspace.domain.document.InlineText
import xyz.robinjoon.agentdocs.workspace.domain.document.history.BlockChangeKind
import xyz.robinjoon.agentdocs.workspace.domain.document.history.ChangeOrigin
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId
import java.time.LocalDate
import java.util.UUID

class DocumentQueryServiceTest {
    private val documents = mockk<DocumentRepository>()
    private val revisions = mockk<RevisionCatalog>()
    private val spaces = mockk<SpaceRepository>()
    private val threads = mockk<ThreadRepository>()
    private val views = mockk<DocumentViewReporter>()
    private val service = DocumentQueryService(documents, revisions, spaces, threads, views, WorkspaceFixtures.clock)
    private val intro = Heading(BlockId("head01"), 1, InlineText("Intro"))
    private val details = Heading(BlockId("head02"), 2, InlineText("Details"))
    private val next = Heading(BlockId("head03"), 1, InlineText("Next"))
    private val blocks = listOf(intro, paragraph("para01", "a"), details, paragraph("para02", "b"), next, paragraph("para03", "c"))

    @Test
    fun `a section runs to the next heading of the same or a higher level`() {
        every { documents.findById(documentId) } returns document(blocks, revision = 2)
        every { spaces.findById(spaceId) } returns space

        val chunk = service.markdown(ownerAccess, documentId, MarkdownRequest(MarkdownFormat.PLAIN, intro.id, null, 0))

        assertThat(chunk.text).endsWith("# Intro\n\na\n\n## Details\n\nb\n")
        assertThat(chunk.text).startsWith("---\nid: $documentId\nspace: agent-docs\n")
        assertThat(chunk.nextCursor).isNull()
    }

    @Test
    fun `a long document comes in chunks that continue from the cursor`() {
        val long = (1..30).map { paragraph("long%02d".format(it), "x".repeat(3_000)) }
        every { documents.findById(documentId) } returns document(long, revision = 1)
        every { spaces.findById(spaceId) } returns space

        val first = service.markdown(ownerAccess, documentId, MarkdownRequest(MarkdownFormat.ANNOTATED, null, null, 0))
        val second =
            service.markdown(
                ownerAccess,
                documentId,
                MarkdownRequest(MarkdownFormat.ANNOTATED, null, null, requireNotNull(first.nextCursor)),
            )

        assertThat(first.nextCursor).isEqualTo(13)
        assertThat(second.nextCursor).isEqualTo(26)
        assertThat(second.text).startsWith("<!-- b:long14 ")
        assertThat(second.totalBlocks).isEqualTo(30)
    }

    @Test
    fun `asking for a block the document does not have explains how to find ids`() {
        every { documents.findById(documentId) } returns document(blocks, revision = 2)
        every { spaces.findById(spaceId) } returns space

        assertThatThrownBy {
            service.markdown(
                ownerAccess,
                documentId,
                MarkdownRequest(MarkdownFormat.PLAIN, null, listOf(BlockId("gone99")), 0),
            )
        }.isInstanceOfSatisfying(
            InvalidRequestException::class.java,
        ) { assertThat(it.fix).isEqualTo("read the outline to find block ids") }
    }

    @Test
    fun `opening a document remembers the day a person opened it but not an agent`() {
        every { documents.findById(documentId) } returns document(blocks, revision = 2)
        every { spaces.findById(spaceId) } returns space
        every { revisions.marks(documentId) } returns emptyList()
        every { views.record(documentId, "robin", LocalDate.of(2026, 10, 18)) } just runs

        val byPerson = service.open(ownerAccess, documentId)
        val byAgent = service.open(agentAccess(Permission.READ, setOf(spaceId)), documentId)

        assertThat(byPerson.space).isEqualTo(space)
        assertThat(byAgent.document.revision).isEqualTo(2)
    }

    @Test
    fun `a diff to the current revision lists block changes and a unified diff naming each block`() {
        every { documents.findById(documentId) } returns
            document(listOf(paragraph("para01", "new"), paragraph("para03", "added")), revision = 3)
        every { revisions.find(documentId, 1) } returns
            revision(1, listOf(paragraph("para01", "old"), paragraph("para02", "gone")), ChangeOrigin(owner, ChangeSource.WEB), now)

        val diff = service.diff(ownerAccess, documentId, 1, null)

        assertThat(diff.changes.map { it.blockId.value to it.kind }).containsExactly(
            "para01" to BlockChangeKind.MODIFIED,
            "para02" to BlockChangeKind.REMOVED,
            "para03" to BlockChangeKind.ADDED,
        )
        assertThat(diff.unified).isEqualTo(
            "--- rev 1\n+++ rev 3\n" +
                "@@ -1,1 +1,1 @@ b:para01\n-old\n+new\n" +
                "@@ -3,1 +2,0 @@ b:para02\n-gone\n" +
                "@@ -2,0 +3,1 @@ b:para03\n+added\n",
        )
    }

    @Test
    fun `history lists revisions with the threads they answered`() {
        val thread = ThreadId(UUID.randomUUID())
        every { documents.findById(documentId) } returns document(blocks, revision = 2)
        every { threads.linksByRevision(documentId) } returns mapOf(2 to listOf(thread))
        every { revisions.list(documentId, RevisionFilter(null, null, null)) } returns
            listOf(
                2,
                1,
            ).map { RevisionSummary(it, "Retry design", revision(it, blocks, ChangeOrigin(owner, ChangeSource.WEB), now).change) }

        val entries = service.revisions(ownerAccess, documentId, RevisionFilter(null, null, null))

        assertThat(entries.map { it.revision.number to it.threads }).containsExactly(2 to listOf(thread), 1 to emptyList())
    }

    @Test
    fun `a document in a space the caller cannot see is not found`() {
        every { documents.findById(documentId) } returns document(blocks, revision = 2)

        assertThatThrownBy {
            service.revision(agentAccess(Permission.READ, emptySet()), documentId, 1)
        }.isInstanceOf(DocumentNotFoundException::class.java)
    }
}
