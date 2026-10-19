package xyz.robinjoon.agentdocs.workspace.domain.document

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import java.time.Instant
import java.util.UUID

class DocumentTest {
    private val id = DocumentId(UUID.fromString("7a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d"))
    private val space = SpaceId(UUID.fromString("3c1d2e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f"))
    private val header = DocumentHeader(space, DocumentPath("payments/retry-design"), "Retry design", DocumentProperties.initial)
    private val createdAt = Instant.parse("2026-10-18T00:00:00Z")
    private val later = Instant.parse("2026-10-18T00:05:00Z")
    private val intro = Paragraph(BlockId("intro1"), InlineText("Why we retry"))
    private val detail = Paragraph(BlockId("detail"), InlineText("Backoff doubles"))
    private val document = Document.create(id, header, listOf(intro), createdAt)

    @Test
    fun `a new document starts at revision one`() {
        assertThat(document.lifecycle).isEqualTo(DocumentLifecycle(1, createdAt, createdAt, null))
        assertThat(document.blocks).containsExactly(intro)
    }

    @Test
    fun `a revision counts up and an amendment keeps the number`() {
        val revised = document.revise(header, listOf(intro, detail), later)
        val amended = revised.amend(header, listOf(detail), later.plusSeconds(10))

        assertThat(revised.revision).isEqualTo(2)
        assertThat(amended.revision).isEqualTo(2)
        assertThat(amended.blocks).containsExactly(detail)
        assertThat(amended.lifecycle.updatedAt).isEqualTo(later.plusSeconds(10))
    }

    @Test
    fun `a revision never moves the document to another space`() {
        val elsewhere = header.copy(spaceId = SpaceId(UUID.fromString("00000000-0000-4000-8000-000000000001")))

        assertThatThrownBy { document.revise(elsewhere, listOf(intro), later) }
            .isInstanceOf(InvalidDocumentException::class.java)
            .hasMessage("a revision cannot move the document to another space")
    }

    @Test
    fun `block ids must be unique because comments and edits address blocks by id`() {
        val copy = Paragraph(BlockId("intro1"), InlineText("Same id"))

        assertThatThrownBy { Document.create(id, header, listOf(intro, copy), createdAt) }
            .isInstanceOf(InvalidDocumentException::class.java)
            .hasMessage("block ids must be unique: intro1")
    }

    @Test
    fun `trashing and untrashing are new revisions and repeating them changes nothing`() {
        val trashed = requireNotNull(document.trash(later))
        val untrashed = requireNotNull(trashed.untrash(later.plusSeconds(1)))

        assertThat(trashed.isTrashed).isTrue()
        assertThat(trashed.revision).isEqualTo(2)
        assertThat(trashed.trash(later)).isNull()
        assertThat(untrashed.isTrashed).isFalse()
        assertThat(untrashed.revision).isEqualTo(3)
        assertThat(untrashed.untrash(later)).isNull()
    }

    @Test
    fun `later changes to the caller's list do not reach the document`() {
        val source = mutableListOf<Block>(intro)
        val created = Document.create(id, header, source, createdAt)

        source += detail

        assertThat(created.blocks).containsExactly(intro)
        assertThat(created.blocks).isUnmodifiable()
    }

    @Test
    fun `a table cell keeps a pipe escaped so the table stays one row per line`() {
        val row = TableRow.of(BlockId("row001"), listOf(InlineText("a | b"), InlineText("c \\| d")))

        assertThat(row.cells.map { it.markdown }).containsExactly("a \\| b", "c \\| d")
    }
}
