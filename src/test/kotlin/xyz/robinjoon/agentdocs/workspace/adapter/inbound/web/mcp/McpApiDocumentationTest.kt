package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.relaxedResponseFields
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import xyz.robinjoon.agentdocs.support.web.RestDocsWebTest
import xyz.robinjoon.agentdocs.support.web.documented
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.agent
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.agentAccess
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.body
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.document
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.documentId
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WorkspaceExceptionHandler
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

// The protocol and two real tools run against strict use case mocks; tool rules belong to the use case tests. This
// test checks the JSON-RPC shapes of both protocol eras and how tool failures come back.
class McpApiDocumentationTest : RestDocsWebTest() {
    private val queries = mockk<DocumentQueryUseCase>()
    private val documents = mockk<DocumentUseCase>()
    private val settings = McpSettings(setOf("https://docs.example.com"), "0.1.0")
    private val protocol = McpProtocol(listOf(DocsReadTool(queries), DocsEditTool(documents)), JsonMapper.builder().build(), settings)
    private val mockMvc = restDocsMockMvc(McpController(protocol, settings), WorkspaceExceptionHandler())
    private val mcpContext = WriteContext(agentAccess, ChangeSource.MCP, null, null)

    @Test
    fun `initialize answers the 2025 lifecycle with the requested version and the instructions`() {
        call(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"codex-mcp-client","version":"0.163.0"}}}""",
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.result.protocolVersion").value("2025-06-18"))
            .andExpect(jsonPath("$.result.serverInfo.name").value("agent-docs"))
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
                        fieldWithPath(
                            "result.protocolVersion",
                        ).description("2025-11-25, 2025-06-18, 2025-03-26 중 요청한 버전. 모르는 버전이면 2025-11-25"),
                        fieldWithPath("result.capabilities.tools.listChanged").description("false. 도구 목록은 바뀌지 않는다"),
                        subsectionWithPath("result.serverInfo").description("name, title, version"),
                        fieldWithPath("result.instructions").description("읽기·편집·댓글 절차 안내"),
                    ),
                ),
            )
    }

    @Test
    fun `server discover answers the stateless 2026-07-28 protocol`() {
        call(modern("server/discover", 2, "{}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result.resultType").value("complete"))
            .andExpect(jsonPath("$.result.supportedVersions[0]").value("2026-07-28"))
            .andDo(
                documented(
                    "mcp-discover",
                    relaxedResponseFields(
                        fieldWithPath("result.resultType").description("complete"),
                        fieldWithPath("result.supportedVersions").description("2026-07-28"),
                        subsectionWithPath("result.capabilities").description("tools"),
                        fieldWithPath("result.instructions").description("안내"),
                        fieldWithPath("result.ttlMs").description("캐시 시간"),
                        fieldWithPath("result.cacheScope").description("public"),
                        subsectionWithPath("result._meta").description("io.modelcontextprotocol/serverInfo"),
                    ),
                ),
            )
    }

    @Test
    fun `tools list describes every tool with its input schema and hints`() {
        call("""{"jsonrpc":"2.0","id":3,"method":"tools/list","params":{}}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result.tools[0].name").value("docs_read"))
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
    fun `a tool call returns text for the model and the same data as structured content`() {
        val request = MarkdownRequest(MarkdownFormat.ANNOTATED, null, null, 0)
        every { queries.markdown(agentAccess, documentId, request) } returns
            MarkdownChunk(document(), "---\nid: $documentId\n---\n\n<!-- b:para01 h:1a2b3c4d -->\n본문\n", null, 2)

        call("""{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"docs_read","arguments":{"document":"$documentId"}}}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result.isError").value(false))
            .andExpect(jsonPath("$.result.structuredContent.revision").value(3))
            .andDo(
                documented(
                    "mcp-tools-call",
                    relaxedResponseFields(
                        fieldWithPath("result.content[].type").description("text"),
                        fieldWithPath("result.content[].text").description("모델이 읽을 결과. docs_read는 마크다운, 나머지는 JSON"),
                        subsectionWithPath("result.structuredContent").description("같은 결과의 구조화 데이터"),
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
            .andExpect(jsonPath("$.result.structuredContent.code").value("conflict"))
            .andExpect(jsonPath("$.result.structuredContent.conflicts[0].current").value(BlockMarkdown.of(body)))
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
            .andExpect(jsonPath("$.result.structuredContent.revision").value(4))
    }

    @Test
    fun `notifications are accepted, streams are not offered, and foreign origins and versions are refused`() {
        call("""{"jsonrpc":"2.0","method":"notifications/initialized"}""").andExpect(status().isAccepted).andExpect(content().string(""))
        mockMvc.perform(get("/mcp").principal(agent)).andExpect(status().isMethodNotAllowed)
        mockMvc
            .perform(
                post(
                    "/mcp",
                ).principal(
                    agent,
                ).header(
                    "Origin",
                    "https://evil.example",
                ).contentType(MediaType.APPLICATION_JSON)
                    .content("""{"jsonrpc":"2.0","id":1,"method":"ping"}"""),
            ).andExpect(status().isForbidden)
        call(
            """{"jsonrpc":"2.0","id":6,"method":"tools/list","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2099-01-01"}}}""",
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value(-32022))
            .andExpect(jsonPath("$.error.data.supported[0]").value("2026-07-28"))
    }

    private fun call(body: String): ResultActions =
        mockMvc.perform(post("/mcp").principal(agent).contentType(MediaType.APPLICATION_JSON).content(body))

    private fun modern(
        method: String,
        id: Int,
        params: String,
    ): String =
        """{"jsonrpc":"2.0","id":$id,"method":"$method","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28",""" +
            """"io.modelcontextprotocol/clientCapabilities":{}},"extra":$params}}"""
}
