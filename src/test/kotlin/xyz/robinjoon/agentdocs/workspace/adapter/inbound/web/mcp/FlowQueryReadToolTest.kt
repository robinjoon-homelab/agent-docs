package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.agentAccess
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.document
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.documentId
import xyz.robinjoon.agentdocs.workspace.application.model.FlowQuery
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.MarkdownChunk
import xyz.robinjoon.agentdocs.workspace.application.model.MarkdownFormat
import xyz.robinjoon.agentdocs.workspace.application.model.MarkdownRequest
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentQueryUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.SpaceUseCase
import xyz.robinjoon.agentdocs.workspace.domain.document.ChangeSource

// docs_read's flowQuery: what reaches the use case and which inputs are refused before it.
class FlowQueryReadToolTest {
    private val queries = mockk<DocumentQueryUseCase>()
    private val spaces = mockk<SpaceUseCase>()
    private val json = JsonMapper.builder().build()
    private val context = WriteContext(agentAccess, ChangeSource.MCP, null, null)
    private val tool = DocsReadTool(queries, spaces)

    private fun call(arguments: String) = tool.call(Arguments(json.readTree(arguments)), context)

    @Test
    fun `a flow query becomes an outline request with every filter`() {
        val query = FlowQuery(text = "저장", kind = "button", screen = "M-01", limit = 5, offset = 5)
        val listing = "flowQuery: 6–6 of 6 matches (text \"저장\", kind button, screen M-01) at revision 3\n"
        every {
            queries.markdown(agentAccess, documentId, MarkdownRequest(MarkdownFormat.OUTLINE, null, null, 0, null, query))
        } returns MarkdownChunk(document(), listing, null, 6)
        every { spaces.agentGuide(agentAccess, any()) } returns null

        val result =
            call(
                """{"document":"$documentId","flowQuery":{"text":"저장","kind":"button","screen":"M-01","limit":5,"offset":5}}""",
            )

        assertThat(result.markdown).isEqualTo(listing)
    }

    @Test
    fun `misspelt filters a non outline format and a non object are refused`() {
        assertThatThrownBy { call("""{"document":"$documentId","flowQuery":{"txt":"저장"}}""") }
            .isInstanceOf(InvalidRequestException::class.java)
            .hasMessage("argument flowQuery has unknown fields: txt")
        assertThatThrownBy { call("""{"document":"$documentId","format":"markdown","flowQuery":{"text":"저장"}}""") }
            .isInstanceOf(InvalidRequestException::class.java)
            .hasMessage("a flow query lists candidates; omit format or use outline")
        assertThatThrownBy { call("""{"document":"$documentId","flowQuery":"저장"}""") }
            .isInstanceOf(InvalidRequestException::class.java)
            .hasMessage("argument flowQuery must be an object")
        assertThatThrownBy { call("""{"document":"$documentId","flowQuery":{"text":"저장"},"blocks":["save001"]}""") }
            .isInstanceOf(InvalidRequestException::class.java)
            .hasMessage("pass either a flow query or section/blocks, not both")
    }

    @Test
    fun `the tool schema describes the flow query fields and closes the object`() {
        val flowQuery = tool.definition.properties.getValue("flowQuery") as Map<*, *>
        val fields = checkNotNull(flowQuery["properties"]) as Map<*, *>

        assertThat(flowQuery["type"]).isEqualTo("object")
        assertThat(flowQuery["additionalProperties"]).isEqualTo(false)
        assertThat(fields.keys).containsExactly("text", "kind", "screen", "limit", "offset")
        assertThat(tool.definition.description).contains("flowQuery")
    }
}
