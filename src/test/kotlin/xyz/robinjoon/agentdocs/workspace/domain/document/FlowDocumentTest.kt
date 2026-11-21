package xyz.robinjoon.agentdocs.workspace.domain.document

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.BlockPosition
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.DocumentEditor
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.EditOperation
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.MarkdownReader
import xyz.robinjoon.agentdocs.workspace.domain.thread.Anchor
import xyz.robinjoon.agentdocs.workspace.domain.thread.FlowAnchorTarget
import java.time.Instant
import java.util.UUID

class FlowDocumentTest {
    private val initial = FlowRules.initial("phone", "M")
    private val first = initial[1] as FlowScreen
    private val second = FlowScreen(BlockId("screen2"), "M-12", "second")
    private val element =
        FlowElement(
            BlockId("element1"),
            FlowAppearance.of("button", "primary", null, null),
            FlowPlacement(20, 900, 400, 44),
            FlowDescription("저장 · \\", "value\nsecond", "desc\n\n<!-- b:test -->"),
            FlowBehavior.of(FlowControls(false, null, true), listOf(FlowAction("done", "M-99")), listOf("", "ready, waiting")),
        )
    private val blocks = initial + element + second

    private fun document(content: List<Block> = blocks): Document =
        Document.create(
            DocumentId(UUID.randomUUID()),
            DocumentHeader(SpaceId(UUID.randomUUID()), DocumentPath("flow"), "Flow", DocumentProperties.initial, "flow"),
            content,
            Instant.EPOCH,
        )

    @Test
    fun `flow markdown preserves strings identifiers and hashes exactly`() {
        val written = BlockMarkdown.write(blocks, true)
        val parsed = MarkdownReader.readBlocks(written, "flow").map { it.block }
        assertThat(parsed).isEqualTo(blocks)
        assertThat(BlockMarkdown.write(parsed, true)).isEqualTo(written)
        assertThat(parsed.map(BlockMarkdown::hash)).isEqualTo(blocks.map(BlockMarkdown::hash))
    }

    @Test
    fun `next screen never reuses removed numbers including restoration`() {
        val created = document()
        assertThat((created.blocks.first() as FlowSettings).nextScreen).isEqualTo(13)
        val restored = created.revise(created.header, initial, Instant.EPOCH.plusSeconds(1))
        assertThat((restored.blocks.first() as FlowSettings).nextScreen).isEqualTo(13)
    }

    @Test
    fun `invalid block sequences and type changes are rejected`() {
        listOf(
            emptyList(),
            initial.take(1),
            listOf(initial[0], element, first),
            initial + first.copy(id = BlockId("copy1")),
            initial + Paragraph(BlockId("paragraph1"), InlineText("text")),
        ).forEach { invalid ->
            assertThatThrownBy { document(invalid) }.isInstanceOf(InvalidDocumentException::class.java)
        }
        val created = document()
        assertThatThrownBy { created.revise(created.header.copy(type = "doc"), emptyList(), Instant.EPOCH) }
            .isInstanceOf(InvalidDocumentException::class.java)
    }

    @Test
    fun `screen movement carries elements and deletion leaves no orphan`() {
        val moved = DocumentEditor.apply(blocks, listOf(EditOperation.Move(first.id, BlockMarkdown.hash(first), BlockPosition.End)))
        assertThat(moved.drop(1)).containsExactly(second, first, element)
        val deleted = DocumentEditor.apply(blocks, listOf(EditOperation.Delete(first.id, BlockMarkdown.hash(first))))
        assertThat(deleted.drop(1)).containsExactly(second)
        assertThatThrownBy { DocumentEditor.apply(initial, listOf(EditOperation.Delete(first.id, BlockMarkdown.hash(first)))) }
            .isInstanceOf(InvalidDocumentException::class.java)
    }

    @Test
    fun `warnings do not reject vertical scrolling or missing destinations`() {
        val warnings = FlowWarnings.of(blocks)
        assertThat(warnings.map { it.code }).containsExactly("missingScreen", "outsideFrame")
        assertThat(document().blocks).contains(element)
    }

    @Test
    fun `comments distinguish element deletion screen deletion and ignored placement controls`() {
        val anchor = Anchor.Whole(element.id, element.plainText())
        assertThat(FlowAnchorTarget.of(anchor, blocks, blocks.filterNot { it.id == element.id })).isEqualTo("elementDeleted")
        assertThat(FlowAnchorTarget.of(anchor, blocks, listOf(initial[0], second))).isEqualTo("screenDeleted")
        assertThat(FlowAnchorTarget.of(anchor, blocks, initial + second + element)).isEqualTo("elementDeleted")
        val changed = element.copy(text = element.text.copy(value = "changed"))
        assertThat(FlowAnchorTarget.of(anchor, blocks, initial + changed + second)).isEqualTo("changed")
    }

    @Test
    fun `insertion allocates a screen id and missing coordinates within its frame`() {
        val screenText = BlockMarkdown.of(second).replace("## M-12", "## ")
        val screenInput = MarkdownReader.readBlocks(screenText, "flow").single()
        val created =
            DocumentEditor.apply(
                initial,
                listOf(
                    EditOperation.Insert(
                        BlockPosition.End,
                        listOf(screenInput.block),
                        mapOf(screenInput.block.id to screenInput.flowDefaults),
                    ),
                ),
            )
        assertThat((created.last() as FlowScreen).screenId).isEqualTo("M-02")
        val elementText =
            BlockMarkdown
                .of(
                    element,
                ).lineSequence()
                .filterNot { it.startsWith("- x:") || it.startsWith("- y:") }
                .joinToString("\n")
        val parsed = MarkdownReader.readBlocks(elementText, "flow").single()
        val inserted =
            DocumentEditor.apply(
                initial,
                listOf(
                    EditOperation.Insert(
                        BlockPosition.After(first.id),
                        listOf(parsed.block),
                        mapOf(parsed.block.id to parsed.flowDefaults),
                    ),
                ),
            )
        val placed = inserted.last() as FlowElement
        assertThat(placed.placement.x).isZero()
        assertThat(placed.placement.y).isEqualTo(281)
    }

    @Test
    fun `moving an element to a different screen fails without changing either screen`() {
        assertThatThrownBy {
            DocumentEditor.apply(
                blocks,
                listOf(EditOperation.Move(element.id, BlockMarkdown.hash(element), BlockPosition.After(second.id))),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
        assertThat(blocks).containsExactly(initial[0], first, element, second)
    }

    @Test
    fun `whitespace descriptions and empty strings round trip`() {
        val screen = first.copy(name = "", desc = "   ")
        val written = BlockMarkdown.write(listOf(screen), true)
        assertThat(MarkdownReader.readBlocks(written, "flow").single().block).isEqualTo(screen)
    }

    @Test
    fun `element restoration requires its screen and descriptions accept suggestions`() {
        assertThatThrownBy {
            xyz.robinjoon.agentdocs.workspace.domain.document.history.BlockRestoration.restore(
                listOf(initial[0], second),
                blocks,
                element.id,
            )
        }.isInstanceOf(InvalidDocumentException::class.java)
        val basis =
            xyz.robinjoon.agentdocs.workspace.domain.thread.SuggestionBasis(
                Anchor.Whole(element.id, element.plainText()),
                BlockMarkdown.hash(element),
                element.text.desc,
                "new description",
            )
        val changed =
            xyz.robinjoon.agentdocs.workspace.domain.thread.SuggestionPatch
                .apply(blocks, basis)
        assertThat((changed[2] as FlowElement).text.desc).isEqualTo("new description")
        assertThat((changed[2] as FlowElement).placement).isEqualTo(element.placement)
    }

    @Test
    fun `flow fields reject invalid kinds sizes prefixes and frame dimensions`() {
        listOf<() -> Any>(
            { FlowSettings(BlockId("setting1"), "phone", "lower") },
            { FlowFrame("custom", 199, 800) },
            { FlowFrame("phone", 390, null) },
            { FlowPlacement(-1, 0, 20, 20) },
            { FlowPlacement(0, 0, 15, 20) },
            { FlowAppearance.of("text", "primary", null, null) },
            { FlowAppearance.of("list", null, listOf("x".repeat(201)), null) },
            { FlowAction("x", "missing", "same") },
            { FlowInsertion.prepare(initial, listOf(FlowScreen(BlockId("hugeid"), "M-" + "9".repeat(30))), 2, emptyMap()) },
        ).forEach { invalid -> assertThatThrownBy { invalid() }.isInstanceOf(InvalidDocumentException::class.java) }
    }

    @Test
    fun `all element kinds preserve nullable fields lists states and actions through markdown`() {
        val looks =
            mapOf(
                "title" to "small",
                "field" to "normal",
                "select" to "inactive",
                "area" to "normal",
                "button" to "disabled",
                "notice" to "ok",
                "box" to "outline",
            )
        val kinds =
            listOf(
                "title",
                "text",
                "field",
                "select",
                "area",
                "button",
                "link",
                "list",
                "option",
                "notice",
                "path",
                "bar",
                "crumb",
                "block",
                "box",
                "image",
            )
        kinds.forEach { kind ->
            val appearance =
                FlowAppearance.of(
                    kind,
                    looks[kind],
                    if (kind == "list") listOf("", "  ", "first\nsecond") else null,
                    if (kind == "path") "" else null,
                )
            val block = element.copy(appearance = appearance)
            assertThat(MarkdownReader.readBlocks(BlockMarkdown.write(listOf(block), true), "flow").single().block).isEqualTo(block)
        }
    }

    @Test
    fun `screen comments observe child changes but ignore lock and group changes`() {
        val screenAnchor = Anchor.Whole(first.id, first.plainText())
        val locked =
            element.copy(
                behavior =
                    FlowBehavior.of(
                        FlowControls(true, "group1", true),
                        element.behavior.actions,
                        element.behavior.states,
                    ),
            )
        assertThat(FlowAnchorTarget.of(screenAnchor, blocks, initial + locked + second)).isEqualTo("ok")
        val changed = element.copy(placement = element.placement.copy(x = 24))
        assertThat(FlowAnchorTarget.of(screenAnchor, blocks, initial + changed + second)).isEqualTo("changed")
    }

    @Test
    fun `syntax characters stay readable and Unicode whitespace remains lossless`() {
        val special = "M-05 · #tag <span>"
        val input = element.copy(text = FlowDescription("이름 · 버튼", special, "- leading #tag <x>"))
        val markdown = BlockMarkdown.write(listOf(input), true)
        assertThat(markdown).contains("- value: $special").contains("이름 \\m 버튼")
        assertThat(MarkdownReader.readBlocks(markdown, "flow").single().block).isEqualTo(input)
        listOf("　", " \t　", "\n\r", "　 ").forEach { whitespace ->
            val block = first.copy(name = whitespace, entry = whitespace, desc = whitespace)
            val written = BlockMarkdown.write(listOf(block), true)
            assertThat(MarkdownReader.readBlocks(written, "flow").single().block).isEqualTo(block)
            assertThat(BlockMarkdown.write(MarkdownReader.readBlocks(written, "flow").map { it.block }, true)).isEqualTo(written)
        }
    }

    @Test
    fun `ordinary markdown never interprets flow-looking headings or attributes`() {
        val source = "## M-01 Name\n- entry: path\n- frame: phone"
        assertThat(MarkdownReader.readBlocks(source).map { it.block }).noneMatch { FlowRules.isFlow(it) }
        assertThat(MarkdownReader.readBlocks(source).first().block).isInstanceOf(Heading::class.java)
    }

    @Test
    fun `replace keeps an omitted screen id and element position`() {
        val parsedScreen = MarkdownReader.readBlocks(BlockMarkdown.of(first).replace("## M-01", "## "), "flow").single()
        val screenResult =
            DocumentEditor.apply(
                blocks,
                listOf(
                    EditOperation.Replace(
                        first.id,
                        BlockMarkdown.hash(first),
                        listOf(parsedScreen.block),
                        parsedScreen.flowDefaults,
                    ),
                ),
            )
        assertThat((screenResult[1] as FlowScreen).screenId).isEqualTo("M-01")
        val source =
            BlockMarkdown
                .of(element)
                .lines()
                .filterNot { it.startsWith("- x:") || it.startsWith("- y:") }
                .joinToString("\n")
        val parsedElement = MarkdownReader.readBlocks(source, "flow").single()
        val result =
            DocumentEditor.apply(
                blocks,
                listOf(
                    EditOperation.Replace(
                        element.id,
                        BlockMarkdown.hash(element),
                        listOf(parsedElement.block),
                        parsedElement.flowDefaults,
                    ),
                ),
            )
        assertThat((result[2] as FlowElement).placement).isEqualTo(element.placement)
    }

    @Test
    fun `screen targets compare stored frame and children by id instead of stacking order`() {
        val another = element.copy(id = BlockId("element2"))
        val original = initial + element + another + second
        val reordered = initial + another + element + second
        val anchor = Anchor.Whole(first.id, first.plainText())
        assertThat(FlowAnchorTarget.of(anchor, original, reordered)).isEqualTo("ok")
        val newDefault = (initial[0] as FlowSettings).copy(frame = "desktop")
        assertThat(FlowAnchorTarget.of(anchor, original, listOf(newDefault) + original.drop(1))).isEqualTo("ok")
        val explicit = first.copy(frame = FlowFrame("phone"))
        assertThat(FlowAnchorTarget.of(anchor, original, listOf(initial[0], explicit, element, another, second))).isEqualTo("changed")
    }

    @Test
    fun `replaceText finds original names and reports missing text as a conflict`() {
        val renamed = DocumentEditor.apply(blocks, listOf(EditOperation.ReplaceText(first.id, BlockMarkdown.hash(first), "새 화면", "화면 - A")))
        assertThat((renamed[1] as FlowScreen).name).isEqualTo("화면 - A")
        assertThatThrownBy {
            DocumentEditor.apply(
                blocks,
                listOf(EditOperation.ReplaceText(element.id, BlockMarkdown.hash(element), "not present", "x")),
            )
        }.isInstanceOf(xyz.robinjoon.agentdocs.workspace.domain.document.edit.EditConflictException::class.java)
    }

    @Test
    fun `omitted attributes take kind defaults and write back in full`() {
        val parsed = MarkdownReader.readBlocks("### 상자 · box · 모양만\n- w: 120\n- h: 40\n\n## M-02 빈 화면", "flow").map { it.block }
        val box = parsed[0] as FlowElement
        assertThat(box.appearance.look).isEqualTo("white")
        assertThat(box.text.value).isEmpty()
        assertThat(box.appearance.date).isNull()
        assertThat(box.behavior.pinned).isFalse()
        assertThat(box.behavior.group).isNull()
        assertThat(box.behavior.annotated).isFalse()
        val screen = parsed[1] as FlowScreen
        assertThat(screen.entry).isEmpty()
        assertThat(screen.frame).isEqualTo(FlowFrame())
        val written = BlockMarkdown.write(parsed, true)
        assertThat(written).contains("- look: white", "- pinned: false", "- frameW:")
        assertThat(MarkdownReader.readBlocks(written, "flow").map { it.block }).isEqualTo(parsed)
    }

    @Test
    fun `replace keeps omitted attributes and changes only what was sent`() {
        val screen = first.copy(entry = "사이드바", frame = FlowFrame("tabletP"))
        val path =
            FlowElement(
                BlockId("path1"),
                FlowAppearance.of("path", null, null, "2026-11-20"),
                FlowPlacement(18, 400, 354, 46),
                FlowDescription("경로", "agent-docs/메모", "old"),
                FlowBehavior.of(FlowControls(true, "g1", true), emptyList(), emptyList()),
            )
        val source = listOf(initial[0], screen, path)
        val parsedScreen = MarkdownReader.readBlocks("## M-01 새 이름", "flow").single()
        val parsedPath = MarkdownReader.readBlocks("### 경로 · path\n- w: 200\n- h: 46\n\nnew", "flow").single()
        val result =
            DocumentEditor.apply(
                source,
                listOf(
                    EditOperation.Replace(screen.id, BlockMarkdown.hash(screen), listOf(parsedScreen.block), parsedScreen.flowDefaults),
                    EditOperation.Replace(path.id, BlockMarkdown.hash(path), listOf(parsedPath.block), parsedPath.flowDefaults),
                ),
            )
        assertThat(result[1]).isEqualTo(screen.copy(name = "새 이름"))
        assertThat(result[2]).isEqualTo(
            path.copy(placement = path.placement.copy(w = 200), text = path.text.copy(desc = "new")),
        )
    }

    @Test
    fun `parse errors name the block that failed`() {
        assertThatThrownBy { MarkdownReader.readBlocks("### 저장 · button\n- w: wide\n- h: 40", "flow") }
            .hasMessageContaining("invalid flow integer w")
            .hasMessageContaining("### 저장 · button")
        assertThatThrownBy { MarkdownReader.readBlocks("### 상자 · box\n- look:\n- w: 20\n- h: 20", "flow") }
            .hasMessageContaining("invalid flow appearance in block \"### 상자 · box\"")
    }

    @Test
    fun `note pages hold long text but no elements and cannot be the document default`() {
        val note = second.copy(frame = FlowFrame(FlowFrame.NOTE), desc = "| 연산 | 화면 |\n".repeat(1000).take(FlowScreen.MAX_DESCRIPTION))
        assertThat(document(initial + note).blocks).contains(note)
        assertThatThrownBy { document(initial + note + element) }.hasMessageContaining("a note page cannot contain elements")
        assertThatThrownBy { FlowSettings(BlockId("settings2"), FlowFrame.NOTE) }.isInstanceOf(InvalidDocumentException::class.java)
        assertThatThrownBy {
            second.copy(
                desc = "x".repeat(FlowScreen.MAX_DESCRIPTION + 1),
            )
        }.isInstanceOf(InvalidDocumentException::class.java)
    }

    @Test
    fun `image placeholders whose value is an asset id count as asset references`() {
        val id = UUID.randomUUID()
        val image =
            element.copy(
                appearance = FlowAppearance.of("image", null, null, null),
                text = FlowDescription("화면 캡처", "asset:$id", ""),
            )
        val caption = image.copy(id = BlockId("caption1"), text = FlowDescription("자리", "이미지", ""))
        val text = image.copy(id = BlockId("text1"), appearance = FlowAppearance.of("text", null, null, null))
        assertThat(AssetReferences.of(listOf(image, caption, text)).map { it.value }).containsExactly(id)
    }
}
