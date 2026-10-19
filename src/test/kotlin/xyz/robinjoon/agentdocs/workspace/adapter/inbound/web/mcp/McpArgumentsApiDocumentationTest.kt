package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp

import io.mockk.every
import io.mockk.mockk
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.relaxedResponseFields
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import xyz.robinjoon.agentdocs.config.McpConfig
import xyz.robinjoon.agentdocs.support.web.RestDocsWebTest
import xyz.robinjoon.agentdocs.support.web.documented
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.agent
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.agentAccess
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.at
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.documentId
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.ownerAccess
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.spaceId
import xyz.robinjoon.agentdocs.workspace.application.model.ThreadQuery
import xyz.robinjoon.agentdocs.workspace.application.model.ThreadView
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentQueryUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.ThreadUseCase
import xyz.robinjoon.agentdocs.workspace.domain.thread.Anchor
import xyz.robinjoon.agentdocs.workspace.domain.thread.AnchorResolution
import xyz.robinjoon.agentdocs.workspace.domain.thread.Comment
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentId
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentThread
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadLocation
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadStatus
import java.util.UUID
import org.junit.jupiter.params.provider.Arguments as TestArguments

class McpArgumentsApiDocumentationTest : RestDocsWebTest() {
    private val threads = mockk<ThreadUseCase>()
    private val queries = mockk<DocumentQueryUseCase>()
    private val documents = mockk<DocumentUseCase>()
    private val tools =
        listOf(
            CommentsQueryTool(threads, mockk()),
            CommentsWriteTool(threads),
            DocsReadTool(queries),
            DocsListTool(queries),
            DocsCreateTool(documents),
            DocsEditTool(documents),
            DocsMoveTool(documents),
            DocsHistoryTool(queries, documents),
            DocsSearchTool(mockk()),
            DocsUploadTool(mockk()),
            ChangesSinceTool(mockk()),
            SpacesResolveTool(mockk()),
        )
    private val json = JsonMapper.builder().build()
    private val config = McpConfig()
    private val settings = McpSettings(setOf("https://docs.example.com"), "0.1.0")

    // Building the server installs it as the transport's handler.
    private val transport = config.mcpTransport(json, settings).also { config.mcpServer(it, tools, json, settings) }
    private val mockMvc = restDocsMockMvc(config.mcpRoutes(transport))
    private val thread =
        CommentThread.open(
            ThreadId(UUID.fromString("66666666-6666-4666-8666-666666666666")),
            ThreadLocation(spaceId, documentId, Anchor.Document, 3),
            Comment.write(CommentId(UUID.fromString("77777777-7777-4777-8777-777777777777")), ownerAccess.actor, "확인해 주세요", at),
            at,
        )

    @Test
    fun `a scalar comment status fails the input schema as a tool error`() {
        call("comments_query", """{"status":"resolved"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result.isError").value(true))
            .andExpect(jsonPath("$.result.content[0].text", containsString("comments_query")))
            .andExpect(jsonPath("$.result.content[0].text", containsString("input validation failed")))
            .andDo(
                documented(
                    "mcp-invalid-argument",
                    requestFields(
                        fieldWithPath("jsonrpc").description("2.0"),
                        fieldWithPath("id").description("요청 ID"),
                        fieldWithPath("method").description("tools/call"),
                        fieldWithPath("params.name").description("comments_query"),
                        fieldWithPath("params.arguments.status").description("잘못된 문자열 예시. [\"resolved\"]처럼 문자열 배열로 전달한다"),
                    ),
                    relaxedResponseFields(
                        fieldWithPath("result.isError").description("입력이 도구의 inputSchema에 맞지 않으면 true"),
                        fieldWithPath("result.content[].text").description("어느 인자가 어떻게 어긋났는지 알려 주는 검증 메시지"),
                    ),
                ),
            )
    }

    @Test
    fun `a string array of resolved statuses returns resolved threads`() {
        val resolved = requireNotNull(thread.resolve(ownerAccess.actor, at))
        every { threads.query(agentAccess, ThreadQuery(null, null, setOf(ThreadStatus.RESOLVED), null, null, null)) } returns
            listOf(ThreadView(resolved, AnchorResolution.Attached(null, null), null))

        call("comments_query", """{"status":["resolved"]}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result.isError").value(false))
            .andExpect(jsonPath("$.result.content[0].text", containsString("\"id\":\"${thread.id}\"")))
            .andExpect(jsonPath("$.result.content[0].text", containsString("\"status\":\"resolved\"")))
    }

    @Test
    fun `a missing status queries open threads`() {
        every { threads.query(agentAccess, ThreadQuery(null, null, setOf(ThreadStatus.OPEN), null, null, null)) } returns
            listOf(ThreadView(thread, AnchorResolution.Attached(null, null), null))

        call("comments_query", "{}")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result.isError").value(false))
            .andExpect(jsonPath("$.result.content[0].text", containsString("\"status\":\"open\"")))
    }

    @ParameterizedTest
    @MethodSource("invalidArguments")
    fun `tools reject arguments that do not match their input schema`(
        tool: String,
        arguments: String,
        name: String,
    ) {
        call(tool, arguments)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result.isError").value(true))
            .andExpect(jsonPath("$.result.content[0].text", containsString("input validation failed")))
            .andExpect(jsonPath("$.result.content[0].text", containsString(name)))
    }

    private fun call(
        tool: String,
        arguments: String,
    ): ResultActions =
        mockMvc.perform(
            post("/mcp")
                .principal(agent)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"$tool","arguments":$arguments}}"""),
        )

    companion object {
        @JvmStatic
        fun invalidArguments(): List<TestArguments> =
            listOf(
                TestArguments.of("comments_query", """{"status":["resolved",1]}""", "status"),
                TestArguments.of("comments_query", """{"status":null}""", "status"),
                TestArguments.of("comments_query", """{"document":1}""", "document"),
                TestArguments.of("comments_query", """{"since":true}""", "since"),
                TestArguments.of("comments_query", """{"opId":1}""", "opId"),
                TestArguments.of("comments_query", """{"summary":[]}""", "summary"),
                TestArguments.of(
                    "comments_write",
                    """{"action":"reply","thread":"$documentId","body":"reply","markAnswered":"true"}""",
                    "markAnswered",
                ),
                TestArguments.of("comments_write", """{"action":"link","revision":"1"}""", "revision"),
                TestArguments.of("docs_read", """{"blocks":"para01"}""", "blocks"),
                TestArguments.of("docs_read", """{"cursor":"1"}""", "cursor"),
                TestArguments.of("docs_list", """{"trashed":"true"}""", "trashed"),
                TestArguments.of("docs_create", """{"tags":[1]}""", "tags"),
                TestArguments.of("docs_edit", """{"answering":true}""", "answering"),
                TestArguments.of("docs_move", """{"tags":"tag"}""", "tags"),
                TestArguments.of("docs_move", """{"baseRevision":"1"}""", "baseRevision"),
                TestArguments.of("docs_move", """{"assignee":false}""", "assignee"),
                TestArguments.of("docs_history", """{"document":"$documentId","action":"diff","from":1,"to":"2"}""", "to"),
                TestArguments.of("docs_search", """{"query":"text","space":false}""", "space"),
                TestArguments.of(
                    "docs_upload",
                    """{"space":"agent-docs","url":"https://example.com/image.png","alt":true}""",
                    "alt",
                ),
                TestArguments.of("changes_since", """{"limit":"100"}""", "limit"),
                TestArguments.of("spaces_resolve", """{"remote":[]}""", "remote"),
            )
    }
}
