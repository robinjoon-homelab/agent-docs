package xyz.robinjoon.agentdocs.workspace.application.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.tokenization.O200kMarkdownTokenCounter
import xyz.robinjoon.agentdocs.workspace.application.model.FlowQuery
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.MarkdownFormat
import xyz.robinjoon.agentdocs.workspace.application.model.MarkdownRequest
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentHeader
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentLifecycle
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentProperties
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowAction
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowAppearance
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowBehavior
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowControls
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowDescription
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowDesignBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowElement
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowPlacement
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowRules
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowScreen
import xyz.robinjoon.agentdocs.workspace.domain.document.InlineText
import xyz.robinjoon.agentdocs.workspace.domain.document.Paragraph
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import java.time.Instant
import java.util.UUID

class FlowTargetsTest {
    private val pages = MarkdownPages(O200kMarkdownTokenCounter(), "https://docs.example/")
    private val space = SpaceSlug("my-space")
    private val documentId = DocumentId(UUID.fromString("00000000-0000-4000-8000-000000000001"))

    private val initial = FlowRules.initial("phone", "M")
    private val first = (initial[1] as FlowScreen).copy(name = "새 메모 작성", entry = "사이드바 › 빠른 메모")
    private val title = element("title01", "title", "새 메모", Extra(annotated = false))
    private val space1 = element("select1", "select", "스페이스")
    private val tags = element("list001", "list", "태그", Extra(desc = "고른 스페이스의 태그", items = listOf("기획", "개발")))
    private val save = element("save001", "button", "저장", Extra(desc = "메모를 저장한다", to = "M-05"))
    private val done = FlowScreen(BlockId("screen5"), "M-05", "저장 완료")
    private val again = element("again01", "button", "새 메모", Extra(desc = "처음 화면으로", to = "M-01"))
    private val blocks = listOf(initial[0], first, title, space1, tags, save, done, again)

    private class Extra(
        val desc: String = "",
        val to: String? = null,
        val items: List<String>? = null,
        val annotated: Boolean = true,
    )

    private fun element(
        id: String,
        kind: String,
        label: String,
        extra: Extra = Extra(),
    ): FlowElement =
        FlowElement(
            BlockId(id),
            FlowAppearance.of(kind, FlowAppearance.defaultLook(kind), extra.items, null),
            FlowPlacement(20, 100, 285, 42),
            FlowDescription(label, "", extra.desc),
            FlowBehavior.of(
                FlowControls(false, null, extra.annotated),
                listOfNotNull(extra.to?.let { FlowAction("click", it) }),
                emptyList(),
            ),
        )

    private fun header(type: String = "flow") =
        DocumentHeader(SpaceId(UUID.randomUUID()), DocumentPath("flow"), "Flow", DocumentProperties.initial, type)

    private fun document(content: List<Block> = blocks): Document = Document.create(documentId, header(), content, Instant.EPOCH)

    // Stored as given, without the flow normalization.
    private fun stored(
        content: List<Block>,
        type: String,
    ): Document = Document.restore(documentId, header(type), content, DocumentLifecycle.started(Instant.EPOCH))

    private fun query(
        query: FlowQuery,
        revision: Int? = null,
        document: Document = document(),
    ): String = pages.chunk(document, space, MarkdownRequest(MarkdownFormat.OUTLINE, null, null, 0, revision, query)).text

    private fun link(element: FlowElement?): String =
        "https://docs.example/s/my-space/d/${documentId.value}?flowScreen=${if (element == again) "screen5" else first.id}" +
            element?.let { "&flowElement=${it.id}" }.orEmpty()

    @Test
    fun `v4 outline and screen sections use stable IDs and direct readers to scoped design tools`() {
        val config = FlowDesignBlock.parse(BlockId("config01"), """{"id":"settings","kind":"settings","modelVersion":4}""")
        val screen = FlowDesignBlock.parse(BlockId("screen01"), """{"id":"home","kind":"screen","name":"Home","code":"M-01"}""")
        val state = FlowDesignBlock.parse(BlockId("state001"), """{"id":"draft","kind":"state","screen":"home","name":"Draft"}""")
        val other = FlowDesignBlock.parse(BlockId("screen02"), """{"id":"other","kind":"screen","name":"Other","code":"M-02"}""")
        val stored = stored(listOf(config, screen, other, state), "flow")
        val request = MarkdownRequest(MarkdownFormat.OUTLINE, screen.id, null, 0)

        assertThat(pages.chunk(stored, space, request).text).contains("screen home M-01 Home", "state draft Draft").doesNotContain("Other")
        assertThatThrownBy { query(FlowQuery(text = "Home"), document = stored) }.hasMessageContaining("v4")
    }

    @Test
    fun `an element kind keeps only elements of that kind and gives ids hashes screens and links`() {
        val text = query(FlowQuery(text = "저장", kind = "button"))

        assertThat(text).isEqualTo(
            "flowQuery: 1–1 of 1 matches (text \"저장\", kind button) at revision ${document().revision}\n" +
                "- element 저장 · button 285×42 · in M-01 새 메모 작성 (b:${first.id}) · 설명 있음 · → M-05 · match: label, desc " +
                "(b:save001 h:${BlockMarkdown.hash(save)}) ${link(save)}\n",
        )
    }

    @Test
    fun `text alone matches screens and elements in document order and names the matched fields`() {
        val lines = query(FlowQuery(text = "메모")).lines().filter { it.startsWith("- ") }

        assertThat(lines.map { it.substringAfterLast("(b:").substringBefore(" ") })
            .containsExactly(first.id.value, "title01", "save001", "again01")
        assertThat(lines[0]).startsWith("- screen M-01 새 메모 작성 · 요소 4 · ").contains(" · match: name, entry ")
        assertThat(lines[1]).contains(" · 모양만").contains(" · match: label ")
        assertThat(lines[2]).contains(" · match: desc ")
    }

    @Test
    fun `a screen scope accepts a screen id in any case or a screen block id`() {
        val byCode = query(FlowQuery(screen = "m-05"))
        val byBlock = query(FlowQuery(screen = "screen5"))

        assertThat(byCode.substringAfter('\n')).isEqualTo(byBlock.substringAfter('\n'))
        assertThat(byCode.lines().filter { it.startsWith("- ") }.map { it.substringAfterLast("(b:").substringBefore(" ") })
            .containsExactly("screen5", "again01")
        assertThat(byCode).contains("(screen m-05)")
    }

    @Test
    fun `screen and element kinds select a block type and unknown kinds name the choices`() {
        assertThat(
            query(FlowQuery(kind = "screen")).lines().filter { it.startsWith("- ") },
        ).allMatch { it.startsWith("- screen ") }.hasSize(2)
        assertThat(
            query(FlowQuery(kind = "element")).lines().filter { it.startsWith("- ") },
        ).allMatch { it.startsWith("- element ") }.hasSize(5)
        assertThat(query(FlowQuery(kind = "select"))).contains("- element 스페이스 · select").contains(" · 설명 없음")
        assertThat(query(FlowQuery(text = "개발"))).contains("(b:list001 ").contains(" · match: item ")

        assertThatThrownBy { query(FlowQuery(kind = "buton")) }
            .isInstanceOf(InvalidRequestException::class.java)
            .hasMessage("unknown flow kind buton")
            .extracting("fix")
            .asString()
            .contains("screen, element, or one of:")
            .contains("button")
    }

    @Test
    fun `matches beyond the limit are reachable with the offset the last line names`() {
        val firstPage = query(FlowQuery(kind = "element", limit = 3))
        val secondPage = query(FlowQuery(kind = "element", limit = 3, offset = 3))

        assertThat(firstPage).startsWith("flowQuery: 1–3 of 5 matches (kind element)")
        assertThat(firstPage).endsWith("- … 2 more: continue with offset 3, or narrow with text, kind or screen\n")
        assertThat(secondPage).startsWith("flowQuery: 4–5 of 5 matches (kind element)")
        assertThat(secondPage.lines().filter { it.startsWith("- element") }.map { it.substringAfterLast("(b:").substringBefore(" ") })
            .containsExactly("save001", "again01")
        assertThat(query(FlowQuery(kind = "element", offset = 9))).endsWith("- offset 9 is past the last match (5)\n")
        assertThat(query(FlowQuery(text = "없는 글자"))).isEqualTo(
            "flowQuery: 0 of 0 matches (text \"없는 글자\") at revision ${document().revision}\n" +
                "- no match: try shorter text, another kind, or read the outline\n",
        )
    }

    @Test
    fun `a past revision lists candidates without links to the current document`() {
        val text = query(FlowQuery(text = "저장", kind = "button"), revision = 1)

        assertThat(text).contains("(b:save001 h:${BlockMarkdown.hash(save)})\n").doesNotContain("https://")
    }

    @Test
    fun `flow queries reject other document types unknown screens and section reads`() {
        val ordinary = stored(listOf(Paragraph(BlockId("para01"), InlineText("본문"))), "doc")
        assertThatThrownBy { query(FlowQuery(text = "a"), document = ordinary) }
            .isInstanceOf(InvalidRequestException::class.java)
            .hasMessage("flowQuery is only for flow documents")
        assertThatThrownBy { query(FlowQuery(screen = "M-99")) }
            .isInstanceOf(InvalidRequestException::class.java)
            .hasMessage("screen M-99 is not in the document")
        assertThatThrownBy { MarkdownRequest(MarkdownFormat.OUTLINE, BlockId("screen5"), null, 0, null, FlowQuery(text = "a")) }
            .isInstanceOf(InvalidRequestException::class.java)
        assertThatThrownBy { FlowQuery(limit = 101) }.isInstanceOf(InvalidRequestException::class.java)
        assertThatThrownBy { FlowQuery(offset = -1) }.isInstanceOf(InvalidRequestException::class.java)
        assertThatThrownBy { FlowQuery(text = " ") }.isInstanceOf(InvalidRequestException::class.java)
    }

    @Test
    fun `the flow outline keeps its bytes`() {
        val stored = document()
        val outline = pages.chunk(stored, space, MarkdownRequest(MarkdownFormat.OUTLINE, null, null, 0)).text
        // The pre-change rendering: hash and size each computed from their own rendering of the stored block.
        val expected =
            stored.blocks.joinToString("") { block ->
                val details =
                    when (block) {
                        is FlowScreen -> "${block.screenId} ${block.name} · 요소 ${if (block.id == first.id) 4 else 1} · 390×844"
                        is FlowElement -> "${block.text.label} · ${block.appearance.kind} 285×42"
                        else -> "flowSettings phone"
                    }
                "- $details (b:${block.id} h:${BlockMarkdown.hash(block)} ${BlockMarkdown.of(block).length}자)\n"
            }
        assertThat(outline).isEqualTo(expected)
    }
}
