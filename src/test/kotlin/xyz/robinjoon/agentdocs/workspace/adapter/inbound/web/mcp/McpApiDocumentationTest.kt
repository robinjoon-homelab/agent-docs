package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp

import io.mockk.every
import io.mockk.mockk
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.relaxedResponseFields
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import xyz.robinjoon.agentdocs.config.McpConfig
import xyz.robinjoon.agentdocs.support.web.RestDocsWebTest
import xyz.robinjoon.agentdocs.support.web.documented
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.agent
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.agentAccess
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.body
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.document
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.documentId
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentWrite
import xyz.robinjoon.agentdocs.workspace.application.model.MarkdownChunk
import xyz.robinjoon.agentdocs.workspace.application.model.MarkdownFormat
import xyz.robinjoon.agentdocs.workspace.application.model.MarkdownRequest
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentQueryUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentUseCase
import xyz.robinjoon.agentdocs.workspace.domain.document.ChangeSource
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.BlockConflict
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.EditConflictException
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.EditOperation
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown

// The SDK server and transport as McpConfig wires them, with two real tools against strict use case mocks; tool rules
// belong to the use case tests. This test checks the JSON-RPC shapes the endpoint answers and how tool failures return.
class McpApiDocumentationTest : RestDocsWebTest() {
    private val queries = mockk<DocumentQueryUseCase>()
    private val documents = mockk<DocumentUseCase>()
    private val json = JsonMapper.builder().build()
    private val config = McpConfig()
    private val settings = McpSettings(setOf("https://docs.example.com"), "0.1.0")

    // Building the server installs it as the transport's handler.
    private val transport =
        config
            .mcpTransport(
                json,
                settings,
            ).also { config.mcpServer(it, listOf(DocsReadTool(queries), DocsEditTool(documents)), json, settings) }
    private val mockMvc = restDocsMockMvc(config.mcpRoutes(transport))
    private val mcpContext = WriteContext(agentAccess, ChangeSource.MCP, null, null)

    @Test
    fun `initialize answers with the requested version, the tools capability and the instructions`() {
        call(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},""" +
                """"clientInfo":{"name":"codex-mcp-client","version":"0.163.0"}}}""",
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.result.protocolVersion").value("2025-06-18"))
            .andExpect(jsonPath("$.result.serverInfo.name").value("agent-docs"))
            .andExpect(jsonPath("$.result.capabilities.tools.listChanged").value(false))
            .andDo(
                documented(
                    "mcp-initialize",
                    requestFields(
                        fieldWithPath("jsonrpc").description("2.0"),
                        fieldWithPath("id").description("요청 ID"),
                        fieldWithPath("method").description("initialize"),
                        subsectionWithPath("params").description("protocolVersion, capabilities, clientInfo"),
                    ),
                    relaxedResponseFields(
                        fieldWithPath("result.protocolVersion")
                            .description("2025-11-25, 2025-06-18, 2025-03-26 중 요청한 버전. 지원하지 않는 버전이면 2025-11-25"),
                        fieldWithPath("result.capabilities.tools.listChanged").description("false. 도구 목록은 바뀌지 않는다"),
                        subsectionWithPath("result.serverInfo").description("name, title, version"),
                        fieldWithPath("result.instructions").description("읽기·편집·댓글 절차 안내"),
                    ),
                ),
            )
    }

    @Test
    fun `an unsupported version is answered with the latest version the server speaks`() {
        call(
            """{"jsonrpc":"2.0","id":2,"method":"initialize","params":{"protocolVersion":"2099-01-01","capabilities":{},""" +
                """"clientInfo":{"name":"future","version":"1"}}}""",
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.result.protocolVersion").value("2025-11-25"))
    }

    @Test
    fun `tools list describes every tool with its input schema and hints`() {
        call("""{"jsonrpc":"2.0","id":3,"method":"tools/list","params":{}}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result.tools[0].name").value("docs_read"))
            .andExpect(jsonPath("$.result.tools[0].inputSchema.required[0]").value("document"))
            .andExpect(jsonPath("$.result.tools[0].annotations.readOnlyHint").value(true))
            .andExpect(jsonPath("$.result.tools[1].annotations.readOnlyHint").value(false))
            .andDo(
                documented(
                    "mcp-tools-list",
                    relaxedResponseFields(
                        subsectionWithPath("result.tools").description("name, title, description, inputSchema, annotations"),
                    ),
                ),
            )
    }

    @Test
    fun `a document read returns the markdown as the only text and no structured content`() {
        val request = MarkdownRequest(MarkdownFormat.ANNOTATED, null, null, 0)
        val markdown = "---\nid: $documentId\nrevision: 3\n---\n\n<!-- b:para01 h:1a2b3c4d -->\n본문\n"
        every { queries.markdown(agentAccess, documentId, request) } returns MarkdownChunk(document(), markdown, null, 2)

        call("""{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"docs_read","arguments":{"document":"$documentId"}}}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result.isError").value(false))
            .andExpect(jsonPath("$.result.content[0].type").value("text"))
            .andExpect(jsonPath("$.result.content[0].text").value(markdown))
            .andExpect(jsonPath("$.result.structuredContent").doesNotExist())
            .andDo(
                documented(
                    "mcp-tools-call",
                    relaxedResponseFields(
                        fieldWithPath("result.content[].type").description("text"),
                        fieldWithPath("result.content[].text")
                            .description("모델이 읽을 결과. docs_read와 docs_history diff는 마크다운, 나머지는 JSON"),
                        fieldWithPath("result.isError").description("업무 오류면 true"),
                    ),
                ),
            )
    }

    @Test
    fun `a changed block comes back as a tool error with the block as it is now`() {
        val operations = listOf(EditOperation.Delete(body.id, "00000000"))
        every { documents.edit(mcpContext, documentId, operations, null) } throws
            EditConflictException(listOf(BlockConflict(body.id, BlockMarkdown.hash(body), BlockMarkdown.of(body))))

        call(
            """{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"docs_edit","arguments":""" +
                """{"document":"$documentId","operations":[{"op":"delete","blockId":"para01","ifHash":"00000000"}]}}}""",
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.result.isError").value(true))
            .andExpect(jsonPath("$.result.content[0].text", containsString("\"code\":\"conflict\"")))
            .andExpect(jsonPath("$.result.content[0].text", containsString("\"blockId\":\"para01\"")))
            .andDo(documented("mcp-tool-error"))
    }

    @Test
    fun `a write tool passes its opId and summary on to the use case`() {
        val operations = listOf(EditOperation.Delete(body.id, BlockMarkdown.hash(body)))
        val context = mcpContext.copy(operationId = "op-7", summary = "재시도 간격 수정")
        every { documents.edit(context, documentId, operations, null) } returns
            DocumentWrite(document(revision = 4), 4, listOf(body.id), amended = false, unchanged = false)

        call(
            """{"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"docs_edit","arguments":{"document":"$documentId",""" +
                """"opId":"op-7","summary":"재시도 간격 수정","operations":[{"op":"delete","blockId":"para01","ifHash":"${BlockMarkdown.hash(
                    body,
                )}"}]}}}""",
        ).andExpect(jsonPath("$.result.isError").value(false))
            .andExpect(jsonPath("$.result.content[0].text", containsString("\"revision\":4")))
    }

    @Test
    fun `an unknown tool is a protocol error`() {
        call("""{"jsonrpc":"2.0","id":8,"method":"tools/call","params":{"name":"docs_delete","arguments":{}}}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.error.code").value(-32602))
    }

    @Test
    fun `notifications are accepted, streams are not offered, and foreign origins are refused`() {
        call("""{"jsonrpc":"2.0","method":"notifications/initialized"}""").andExpect(status().isAccepted).andExpect(content().string(""))
        mockMvc.perform(get("/mcp").principal(agent)).andExpect(status().isMethodNotAllowed)
        mockMvc
            .perform(mcpPost("""{"jsonrpc":"2.0","id":1,"method":"ping"}""").header("Origin", "https://evil.example"))
            .andExpect(status().isForbidden)
        mockMvc
            .perform(mcpPost("""{"jsonrpc":"2.0","id":1,"method":"ping"}""").header("Origin", "https://docs.example.com"))
            .andExpect(status().isOk)
    }

    private fun call(body: String): ResultActions = mockMvc.perform(mcpPost(body))

    // A Streamable HTTP client must accept both a JSON body and an event stream.
    private fun mcpPost(body: String): MockHttpServletRequestBuilder =
        post("/mcp")
            .principal(agent)
            .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
}
