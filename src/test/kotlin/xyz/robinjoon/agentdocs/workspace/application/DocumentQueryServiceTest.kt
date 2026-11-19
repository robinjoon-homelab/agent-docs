package xyz.robinjoon.agentdocs.workspace.application

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.EncodingType
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.tokenization.O200kMarkdownTokenCounter
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
import xyz.robinjoon.agentdocs.workspace.application.port.output.RevisionCatalog
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.ThreadRepository
import xyz.robinjoon.agentdocs.workspace.application.service.DocumentQueryService
import xyz.robinjoon.agentdocs.workspace.domain.Permission
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.ChangeSource
import xyz.robinjoon.agentdocs.workspace.domain.document.CodeBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.Heading
import xyz.robinjoon.agentdocs.workspace.domain.document.InlineText
import xyz.robinjoon.agentdocs.workspace.domain.document.history.BlockChangeKind
import xyz.robinjoon.agentdocs.workspace.domain.document.history.ChangeOrigin
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.DocumentMarkdown
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId
import java.util.UUID

class DocumentQueryServiceTest {
    private val documents = mockk<DocumentRepository>()
    private val revisions = mockk<RevisionCatalog>()
    private val spaces = mockk<SpaceRepository>()
    private val threads = mockk<ThreadRepository>()
    private val service = DocumentQueryService(documents, revisions, spaces, threads, O200kMarkdownTokenCounter())
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
        assertThat(chunk.text).contains("version: 2\n")
        assertThat(chunk.nextCursor).isNull()
    }

    @Test
    fun `one oversized block yields canonical bytes across token bounded pages`() {
        val code = CodeBlock(BlockId("long01"), "txt", "漢字🙂\n".repeat(15_000))
        val long = document(listOf(intro, code, details), revision = 2)
        every { documents.findById(documentId) } returns long
        every { spaces.findById(spaceId) } returns space
        val encoding = Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.O200K_BASE)
        val request = MarkdownRequest(MarkdownFormat.ANNOTATED, null, null, 0)
        val pages =
            generateSequence(service.markdown(ownerAccess, documentId, request)) { page ->
                page.nextCursor?.let { service.markdown(ownerAccess, documentId, request.copy(cursor = it)) }
            }.toList()

        assertThat(pages).hasSizeGreaterThan(1)
        assertThat(pages).allSatisfy { assertThat(encoding.countTokensOrdinary(it.text)).isLessThanOrEqualTo(20_000) }
        assertThat(pages.joinToString("") { it.text }).isEqualTo(DocumentMarkdown.write(long, space.slug, true))
        val outline = service.markdown(ownerAccess, documentId, request.copy(format = MarkdownFormat.OUTLINE))
        assertThat(outline.text).contains("b:long01", "b:head02")
        assertThat(outline.nextCursor).isNull()
    }

    @Test
    fun `a historical markdown read uses stored contents and the current slug`() {
        every { documents.findById(documentId) } returns document(blocks, revision = 3)
        every { spaces.findById(spaceId) } returns space
        every { revisions.find(documentId, 1) } returns
            revision(
                1,
                listOf(paragraph("para01", "historical")),
                ChangeOrigin(owner, ChangeSource.WEB),
                now,
            )

        val historical = service.markdown(ownerAccess, documentId, MarkdownRequest(MarkdownFormat.PLAIN, null, null, 0, 1))

        assertThat(historical.text).contains("space: agent-docs\n", "version: 1\n").endsWith("\n\nhistorical\n")
        assertThat(historical.document.revision).isEqualTo(1)
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
    fun `opening a document returns its space and content for people and agents`() {
        every { documents.findById(documentId) } returns document(blocks, revision = 2)
        every { spaces.findById(spaceId) } returns space
        every { revisions.marks(documentId) } returns emptyList()

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
                "@@ -1,3 +1,3 @@ b:para01 b:para02 b:para03\n-old\n-\n-gone\n+new\n+\n+added\n",
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
