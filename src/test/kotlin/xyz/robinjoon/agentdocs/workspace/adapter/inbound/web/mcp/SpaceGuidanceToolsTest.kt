package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.agentAccess
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.at
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.document
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.documentId
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.space
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.spaceId
import xyz.robinjoon.agentdocs.workspace.application.model.MarkdownChunk
import xyz.robinjoon.agentdocs.workspace.application.model.MarkdownFormat
import xyz.robinjoon.agentdocs.workspace.application.model.MarkdownRequest
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceGuide
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceOverview
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentQueryUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.SpaceUseCase
import xyz.robinjoon.agentdocs.workspace.domain.document.ChangeSource
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import java.util.UUID

// The tools against strict use case mocks: what an agent receives about a space's agent guide.
class SpaceGuidanceToolsTest {
    private val queries = mockk<DocumentQueryUseCase>()
    private val spaces = mockk<SpaceUseCase>()
    private val json = JsonMapper.builder().build()
    private val context = WriteContext(agentAccess, ChangeSource.MCP, null, null)
    private val guideId = DocumentId(UUID.fromString("77777777-7777-4777-8777-777777777777"))
    private val guide = SpaceGuide.Offered(guideId, "작성 규칙", "guide")
    private val markdown = "---\nid: $documentId\nversion: 3\n---\n\n<!-- b:para01 h:1a2b3c4d -->\n본문\n"

    @Test
    fun `the first chunk keeps the document markdown and adds the guide as a separate note`() {
        every { queries.markdown(agentAccess, documentId, request(0)) } returns MarkdownChunk(document(), markdown, null, 2)
        every { spaces.agentGuide(agentAccess, spaceId) } returns guide

        val result = DocsReadTool(queries, spaces).call(arguments("""{"document":"$documentId"}"""), context)

        assertThat(result.markdown).isEqualTo(markdown)
        assertThat(result.notes).containsExactly(
            "This space's agent guide is [작성 규칙](doc:$guideId). Read it with docs_read before you write in this space, " +
                "and follow its writing rules, terms, and templates.",
        )
    }

    @Test
    fun `later chunks and the guide itself carry no note`() {
        every { queries.markdown(agentAccess, documentId, request(1)) } returns MarkdownChunk(document(), markdown, null, 2)
        every { spaces.agentGuide(agentAccess, spaceId) } returns SpaceGuide.Offered(documentId, "작성 규칙", "guide")
        every { queries.markdown(agentAccess, documentId, request(0)) } returns MarkdownChunk(document(), markdown, null, 2)
        val tool = DocsReadTool(queries, spaces)

        val later = tool.call(arguments("""{"document":"$documentId","cursor":1}"""), context)
        val itself = tool.call(arguments("""{"document":"$documentId"}"""), context)

        assertThat(later.notes).isEmpty()
        assertThat(itself.notes).isEmpty()
        assertThat(itself.markdown).isEqualTo(markdown)
    }

    @Test
    fun `spaces_list names each space's guide and default repository`() {
        val guided = space.guideBy(guideId.value).chooseRepository("homelab/agent-docs")
        every { spaces.list(agentAccess, false) } returns listOf(SpaceOverview(guided, 4, at, guide))

        val result = SpacesListTool(spaces).call(arguments("{}"), context)

        val listed = requireNotNull((requireNotNull(result.data?.get("spaces")) as List<*>).single()) as Map<*, *>
        assertThat(listed["agentGuide"]).isEqualTo(mapOf("documentId" to guideId.value, "title" to "작성 규칙", "path" to "guide"))
        assertThat(listed["agentGuideIssue"]).isNull()
        assertThat(listed["defaultRepository"]).isEqualTo("homelab/agent-docs")
    }

    private fun request(cursor: Int): MarkdownRequest = MarkdownRequest(MarkdownFormat.ANNOTATED, null, null, cursor)

    private fun arguments(value: String): Arguments = Arguments(json.readTree(value))
}
