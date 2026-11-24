package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import xyz.robinjoon.agentdocs.support.web.RestDocsWebTest
import xyz.robinjoon.agentdocs.support.web.documented
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.Arguments
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.FlowDesignReadTool
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentView
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentWrite
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentQueryUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentUseCase
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentHeader
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowDesignBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.DocumentEditor
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.EditOperation
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown

class FlowDesignApiDocumentationTest : RestDocsWebTest() {
    private val queries = mockk<DocumentQueryUseCase>()
    private val documents = mockk<DocumentUseCase>()
    private val actions = FlowDesignActions(queries, documents, mockk(relaxed = true), "https://docs.example")
    private val mvc = restDocsMockMvc(FlowDesignController(actions), WorkspaceExceptionHandler())
    private val id = WebFixtures.documentId
    private val json = JsonMapper.builder().build()
    private val state = block("state001", """{"kind":"state","id":"s56","screen":"edit","name":"아트보드 56","changes":[]}""")
    private val screen =
        block(
            "screen01",
            """{"kind":"screen","id":"edit","name":"Edit","code":"F-01","root":{"id":"device","kind":"box","style":{"overflow":"auto"}}}""",
        )
    private val blocks = listOf(block("settings", """{"kind":"settings","id":"settings","modelVersion":4}"""), screen, state)
    private val original = document(blocks)
    private val url = "https://docs.example/s/agent-docs/d/$id"

    @Test
    fun `a state annotation opens its element in the owning state`() {
        val note =
            block(
                "annot001",
                """{"kind":"annotation","id":"info","screen":"edit","state":"s56","target":{"owner":"edit","node":"device"},"name":"안내"}""",
            )
        every { queries.open(any(), id) } returns DocumentView(document(blocks + note), WebFixtures.space, emptyList())
        val input = json.readTree("""{"document":"$id","kind":"annotation"}""")
        val result = FlowDesignReadTool(actions).call(Arguments(input), WebFixtures.webContext)
        val row = json.readTree(json.writeValueAsString(result.data)).path("items").path(0)
        assertThat(row.path("state").asString()).isEqualTo("state001")
        assertThat(row.path("url").asString()).isEqualTo("$url?flowScreen=screen01&flowState=state001&flowOwner=edit&flowElement=device")
    }

    @Test
    fun `read a contour vertex and patch its state coordinate with the returned IDs and hashes`() {
        val vector =
            block(
                "screen01",
                """
          {"id":"edit","kind":"screen","name":"Edit","code":"F-01","root":{"id":"row","kind":"box","layout":{"mode":"row"},"children":[
            {"id":"ico","kind":"vector","fillRule":"nonzero","contours":[{"id":"outline","closed":false,"vertices":[{"id":"tip","x":3,"y":7}]}]}]}}
        """,
            )
        val design = listOf(blocks.first(), vector, state)
        var latest = document(design)
        every { queries.open(any(), id) } answers { DocumentView(latest, WebFixtures.space, emptyList()) }
        every { documents.edit(any(), id, any(), null) } answers {
            val updated = DocumentEditor.apply(latest.blocks, thirdArg<List<EditOperation>>())
            latest = latest.revise(latest.header, updated, WebFixtures.at.plusSeconds(1))
            DocumentWrite(latest, latest.revision, listOf(state.id), false, false)
        }
        val read = """{"view":"parts","screen":"screen01","state":"state001","target":{"owner":"edit","node":"ico"}}"""
        val result =
            mvc
                .perform(
                    post("/api/documents/{id}/flow-design/read", id)
                        .principal(WebFixtures.owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(read),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.items[0].x").value(3))
                .andExpect(jsonPath("$.items[0].parts[1].id").value("tip"))
                .andDo(documented("flow-design-vertex-read"))
                .andReturn()
        val item = json.readTree(result.response.contentAsString).path("items").path(0)
        val change =
            mapOf(
                "screen" to "screen01",
                "state" to "state001",
                "target" to item.path("target"),
                "parts" to item.path("parts"),
                "guards" to item.path("guards"),
                "path" to "x",
                "value" to 9.5,
            )
        mvc
            .perform(
                post("/api/documents/{id}/flow-design/patch", id)
                    .principal(WebFixtures.owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(change)),
            ).andExpect(status().isOk)
            .andDo(documented("flow-design-vertex-patch"))
        val mcpRead = requireNotNull(json.readTree(read) as? tools.jackson.databind.node.ObjectNode).put("document", id.toString())
        val rows = FlowDesignReadTool(actions).call(Arguments(mcpRead), WebFixtures.webContext).data
        assertThat(
            json
                .readTree(json.writeValueAsString(rows))
                .path("items")
                .path(0)
                .path("x")
                .doubleValue(),
        ).isEqualTo(9.5)
        assertThat((latest.block(state.id) as? FlowDesignBlock)?.json).contains("\"op\":\"detailSet\"")
    }

    @Test
    fun `a selected layer viewer URL alone returns its editable occurrence and a decimal ten limit is valid`() {
        every { queries.open(any(), id) } returns DocumentView(original, WebFixtures.space, emptyList())
        val link = "$url?flowScreen=screen01&flowState=state001&flowOwner=edit&flowElement=device"
        val result =
            FlowDesignReadTool(
                actions,
            ).call(Arguments(json.readTree("""{"document":"$link","view":"nodes","limit":10}""")), WebFixtures.webContext)
        org.assertj.core.api.Assertions
            .assertThat(
                result.data.toString(),
            ).contains("target={owner=edit, node=device}", "state001", "guards=")
    }

    @Test
    fun `an agent can start at a viewer URL and browse a bounded page of matching states`() {
        every { queries.open(any(), id) } returns DocumentView(original, WebFixtures.space, emptyList())
        val body = """{"document":"$url","view":"blocks","kind":"state","query":"56","limit":1}"""
        val result = FlowDesignReadTool(actions).call(Arguments(json.readTree(body)), WebFixtures.webContext)
        org.assertj.core.api.Assertions
            .assertThat(result.data.toString())
            .contains("state001", "screen01", "flowState=state001")
        mvc
            .perform(
                post("/api/documents/{id}/flow-design/read", id)
                    .principal(WebFixtures.owner)
                    .contentType(
                        MediaType.APPLICATION_JSON,
                    ).content("""{"view":"nodes","screen":"screen01","state":"state001","query":"device","limit":1}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].guards.state001").value(BlockMarkdown.hash(state)))
            .andDo(
                documented(
                    "flow-design-read",
                    requestFields(
                        fieldWithPath("view").description("blocks 또는 nodes"),
                        fieldWithPath("screen").description("화면 서버 블록 ID"),
                        fieldWithPath("state").description("상태 서버 블록 ID"),
                        fieldWithPath("query").description("검색할 문구"),
                        fieldWithPath("limit").description("응답 항목 수 1~30"),
                    ),
                    responseFields(
                        fieldWithPath("version").description("읽은 문서 버전"),
                        subsectionWithPath("items").description("일치한 대상 주소·해시·보기 링크"),
                        fieldWithPath("nextCursor").description("다음 쪽. 끝이면 null"),
                    ),
                ),
            )
    }

    @Test
    fun `a state field returns a targeted link and preserves the single-block edit contract`() {
        every { queries.open(any(), id) } returns DocumentView(original, WebFixtures.space, emptyList())
        every { documents.edit(any(), id, any(), null) } answers {
            val updated = DocumentEditor.apply(blocks, thirdArg<List<EditOperation>>()).filterIsInstance<FlowDesignBlock>()
            DocumentWrite(document(updated), 2, listOf(state.id), false, false)
        }
        val payload =
            mapOf(
                "screen" to screen.id.value,
                "state" to state.id.value,
                "target" to mapOf("owner" to "edit", "node" to "device"),
                "guards" to mapOf(state.id.value to BlockMarkdown.hash(state), screen.id.value to BlockMarkdown.hash(screen)),
                "path" to "scroll.y",
                "value" to "end",
                "opId" to "once-56",
            )
        mvc
            .perform(
                post("/api/documents/{id}/flow-design/patch", id)
                    .principal(WebFixtures.owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(payload)),
            ).andExpect(
                status().isOk,
            ).andExpect(jsonPath("$.url").value("$url?flowScreen=screen01&flowState=state001&flowOwner=edit&flowElement=device"))
            .andDo(
                documented(
                    "flow-design-patch",
                    requestFields(
                        fieldWithPath("screen").description("화면 블록 ID"),
                        fieldWithPath("state").description("상태 블록 ID"),
                        subsectionWithPath("target").description("조회에서 받은 정확한 노드 주소"),
                        subsectionWithPath("guards").description("조회에서 받은 의존 블록 해시"),
                        fieldWithPath("path").description("수정할 속성"),
                        fieldWithPath("value").description("변경할 값"),
                        fieldWithPath("opId").description("재시도 키"),
                    ),
                ),
            )
    }

    private fun document(items: List<FlowDesignBlock>): Document =
        Document.create(
            id,
            DocumentHeader(WebFixtures.spaceId, WebFixtures.document().header.path, "설계", WebFixtures.properties, "flow"),
            items,
            WebFixtures.at,
        )

    private fun block(
        key: String,
        text: String,
    ) = FlowDesignBlock.parse(BlockId(key), text)
}
