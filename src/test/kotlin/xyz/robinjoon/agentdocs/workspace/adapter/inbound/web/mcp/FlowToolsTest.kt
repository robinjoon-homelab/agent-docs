package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.EditStatisticsRecorder
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.ThreadJson
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentDraft
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentView
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentWrite
import xyz.robinjoon.agentdocs.workspace.application.model.ThreadView
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentQueryUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.SpaceUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.ThreadUseCase
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentHeader
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowAction
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowAppearance
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowBehavior
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowControls
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowDescription
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowElement
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowPlacement
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowRules
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.EditOperation
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown
import xyz.robinjoon.agentdocs.workspace.domain.thread.Anchor
import xyz.robinjoon.agentdocs.workspace.domain.thread.AnchorResolution
import xyz.robinjoon.agentdocs.workspace.domain.thread.Comment
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentId
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentThread
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadLocation
import java.util.UUID

class FlowToolsTest {
    @Test
    fun `MCP creates a flow without markdown and reports its type and warnings`() {
        val documents = mockk<DocumentUseCase>()
        val recorder = mockk<EditStatisticsRecorder>(relaxed = true)
        every { documents.create(any(), WebFixtures.space.slug, any()) } answers {
            val draft = thirdArg<DocumentDraft>()
            val document =
                Document.create(
                    WebFixtures.documentId,
                    DocumentHeader(WebFixtures.spaceId, draft.path, draft.title, draft.properties, draft.type),
                    draft.blocks,
                    WebFixtures.at,
                )
            DocumentWrite(document, 1, document.blocks.map { it.id }, false, false)
        }
        val json = JsonMapper.builder().build()
        val result =
            DocsCreateTool(documents, recorder).call(
                Arguments(
                    json.readTree(
                        """{"space":"agent-docs","path":"flow","title":"Flow","type":"flow","idPrefix":""}""",
                    ),
                ),
                WebFixtures.webContext,
            )
        assertThat(result.data?.get("type")).isEqualTo("flow")
        assertThat(result.data?.get("warnings")).isEqualTo(emptyList<Any>())
        assertThat(requireNotNull(result.data?.get("blocks")) as List<*>).hasSize(2)
    }

    @Test
    fun `ordinary create still requires markdown`() {
        val tool = DocsCreateTool(mockk(), mockk(relaxed = true))
        val json = JsonMapper.builder().build()
        assertThatThrownBy {
            tool.call(
                Arguments(
                    json.readTree(
                        """{"space":"agent-docs","path":"doc","title":"Doc"}""",
                    ),
                ),
                WebFixtures.webContext,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `docs edit parses flow inserts and reports warnings by block id`() {
        val documents = mockk<DocumentUseCase>()
        val queries = mockk<DocumentQueryUseCase>()
        val base = WebFixtures.document()
        val blocks = FlowRules.initial("phone", "M")
        val flow = Document.create(base.id, base.header.copy(type = "flow"), blocks, WebFixtures.at)
        val element =
            FlowElement(
                BlockId("element1"),
                FlowAppearance.of("button", "primary", null, null),
                FlowPlacement(400, 0, 100, 40),
                FlowDescription("버튼", "버튼", ""),
                FlowBehavior.of(FlowControls(false, null, true), listOf(FlowAction("click", "M-99")), emptyList()),
            )
        val edited = flow.revise(flow.header, blocks + element, WebFixtures.at.plusSeconds(1))
        every { queries.open(any(), flow.id) } returns DocumentView(flow, WebFixtures.space, emptyList())
        every {
            documents.edit(
                any(),
                flow.id,
                match { operations ->
                    (operations.single() as? EditOperation.Insert)?.blocks?.single() is FlowElement
                },
                null,
            )
        } returns DocumentWrite(edited, 2, listOf(element.id), false, false)
        val json = JsonMapper.builder().build()
        val input =
            mapOf(
                "document" to flow.id.toString(),
                "operations" to
                    listOf(
                        mapOf(
                            "op" to "insert",
                            "after" to blocks[1].id.value,
                            "markdown" to BlockMarkdown.of(element),
                        ),
                    ),
            )
        val result =
            DocsEditTool(documents, mockk(relaxed = true), queries).call(
                Arguments(json.readTree(json.writeValueAsString(input))),
                WebFixtures.webContext,
            )
        assertThat(result.data?.get("warnings")).isEqualTo(
            listOf("missingDescription", "missingScreen", "outsideFrame")
                .map { mapOf("blockId" to element.id.value, "code" to it) },
        )
    }

    @Test
    fun `comments query requests and returns flow targets while REST serialization omits them`() {
        val threads = mockk<ThreadUseCase>()
        val spaces = mockk<SpaceUseCase>()
        val thread =
            CommentThread.open(
                ThreadId(UUID.randomUUID()),
                ThreadLocation(WebFixtures.spaceId, WebFixtures.documentId, Anchor.Document, 1),
                Comment.write(CommentId(UUID.randomUUID()), WebFixtures.ownerAccess.actor, "Review", WebFixtures.at),
                WebFixtures.at,
            )
        val view = ThreadView(thread, AnchorResolution.Attached(null, null), null, target = "changed")
        every { threads.query(any(), match { it.flowTargets }) } returns listOf(view)
        val result = CommentsQueryTool(threads, spaces).call(Arguments(JsonMapper.builder().build().readTree("{}")), WebFixtures.webContext)
        val expected = ThreadJson.thread(view) + ("target" to "changed")
        assertThat(result.data?.get("threads")).isEqualTo(listOf(expected))
        assertThat(ThreadJson.thread(view)).doesNotContainKey("target")
    }
}
