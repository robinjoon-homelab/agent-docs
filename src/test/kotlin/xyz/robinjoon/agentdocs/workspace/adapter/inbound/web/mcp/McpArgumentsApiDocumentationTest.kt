package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.relaxedResponseFields
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import xyz.robinjoon.agentdocs.support.web.RestDocsWebTest
import xyz.robinjoon.agentdocs.support.web.documented
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.agent
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.agentAccess
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.at
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.documentId
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.ownerAccess
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.spaceId
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WorkspaceExceptionHandler
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
    private val settings = McpSettings(setOf("https://docs.example.com"), "0.1.0")
    private val protocol = McpProtocol(tools, JsonMapper.builder().build(), settings)
    private val mockMvc = restDocsMockMvc(McpController(protocol, settings), WorkspaceExceptionHandler())
    private val thread =
        CommentThread.open(
            ThreadId(UUID.fromString("66666666-6666-4666-8666-666666666666")),
            ThreadLocation(spaceId, documentId, Anchor.Document, 3),
            Comment.write(CommentId(UUID.fromString("77777777-7777-4777-8777-777777777777")), ownerAccess.actor, "확인해 주세요", at),
            at,
        )

    @Test
    fun `a scalar comment status returns an invalid tool error with a fix`() {
        call("comments_query", """{"status":"resolved"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result.isError").value(true))
            .andExpect(jsonPath("$.result.structuredContent.code").value("invalid"))
            .andExpect(jsonPath("$.result.structuredContent.message").value("argument status must be an array of strings"))
            .andExpect(jsonPath("$.result.structuredContent.fix").value("correct the value the message names and send the request again"))
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
                        fieldWithPath("result.isError").description("잘못된 인자 타입이면 true"),
                        fieldWithPath("result.structuredContent.code").description("invalid"),
                        fieldWithPath("result.structuredContent.message").description("잘못된 인자 이름과 기대 타입"),
                        fieldWithPath("result.structuredContent.fix").description("인자를 고쳐 다시 요청하는 방법"),
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
            .andExpect(jsonPath("$.result.structuredContent.threads[0].id").value(thread.id.toString()))
            .andExpect(jsonPath("$.result.structuredContent.threads[0].status").value("resolved"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["{}", "{\"status\":null}"])
    fun `missing and null statuses still query open threads`(arguments: String) {
        every { threads.query(agentAccess, ThreadQuery(null, null, setOf(ThreadStatus.OPEN), null, null, null)) } returns
            listOf(ThreadView(thread, AnchorResolution.Attached(null, null), null))

        call("comments_query", arguments)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result.isError").value(false))
            .andExpect(jsonPath("$.result.structuredContent.threads[0].status").value("open"))
    }

    @ParameterizedTest
    @MethodSource("invalidArguments")
    fun `tools reject invalid optional arguments through the existing error contract`(
        tool: String,
        arguments: String,
        name: String,
        type: String,
    ) {
        call(tool, arguments)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result.isError").value(true))
            .andExpect(jsonPath("$.result.structuredContent.code").value("invalid"))
            .andExpect(jsonPath("$.result.structuredContent.message").value("argument $name must be $type"))
            .andExpect(jsonPath("$.result.structuredContent.fix").value("correct the value the message names and send the request again"))
    }

    private fun call(
        tool: String,
        arguments: String,
    ): ResultActions =
        mockMvc.perform(
            post("/mcp")
                .principal(agent)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"$tool","arguments":$arguments}}"""),
        )

    companion object {
        @JvmStatic
        fun invalidArguments(): List<TestArguments> =
            listOf(
                TestArguments.of("comments_query", """{"status":["resolved",1]}""", "status", "an array of strings"),
                TestArguments.of("comments_query", """{"document":1}""", "document", "a string"),
                TestArguments.of("comments_query", """{"since":true}""", "since", "a string"),
                TestArguments.of("comments_query", """{"opId":1}""", "opId", "a string"),
                TestArguments.of("comments_query", """{"summary":[]}""", "summary", "a string"),
                TestArguments.of(
                    "comments_write",
                    """{"action":"reply","thread":"$documentId","body":"reply","markAnswered":"true"}""",
                    "markAnswered",
                    "a boolean",
                ),
                TestArguments.of("comments_write", """{"action":"link","revision":"1"}""", "revision", "an integer"),
                TestArguments.of("docs_read", """{"blocks":"para01"}""", "blocks", "an array of strings"),
                TestArguments.of("docs_read", """{"cursor":"1"}""", "cursor", "an integer"),
                TestArguments.of("docs_list", """{"trashed":"true"}""", "trashed", "a boolean"),
                TestArguments.of("docs_create", """{"tags":[1]}""", "tags", "an array of strings"),
                TestArguments.of("docs_edit", """{"answering":true}""", "answering", "a string"),
                TestArguments.of("docs_move", """{"tags":"tag"}""", "tags", "an array of strings"),
                TestArguments.of("docs_move", """{"baseRevision":"1"}""", "baseRevision", "an integer"),
                TestArguments.of("docs_move", """{"assignee":false}""", "assignee", "a string"),
                TestArguments.of("docs_history", """{"document":"$documentId","action":"diff","from":1,"to":"2"}""", "to", "an integer"),
                TestArguments.of("docs_search", """{"query":"text","space":false}""", "space", "a string"),
                TestArguments.of(
                    "docs_upload",
                    """{"space":"agent-docs","url":"https://example.com/image.png","alt":true}""",
                    "alt",
                    "a string",
                ),
                TestArguments.of("changes_since", """{"limit":"100"}""", "limit", "an integer"),
                TestArguments.of("spaces_resolve", """{"remote":[]}""", "remote", "a string"),
            )
    }
}
