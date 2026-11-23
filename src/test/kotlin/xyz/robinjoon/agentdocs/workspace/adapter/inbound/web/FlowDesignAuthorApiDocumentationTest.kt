package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.JsonFieldType
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
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.FlowDesignAuthorTool
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentView
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentWrite
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentQueryUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.StaleRevisionException
import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentHeader
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowDesignBlock

class FlowDesignAuthorApiDocumentationTest : RestDocsWebTest() {
    private val queries = mockk<DocumentQueryUseCase>()
    private val documents = mockk<DocumentUseCase>()
    private val actions = FlowDesignAuthorActions(queries, documents, mockk(relaxed = true), "https://docs.example")
    private val mvc = restDocsMockMvc(FlowDesignAuthorController(actions), WorkspaceExceptionHandler())
    private val json = JsonMapper.builder().build()
    private val id = WebFixtures.documentId
    private val route = "/api/documents/{id}/flow-design/author"
    private val settings =
        FlowDesignBlock.parse(
            BlockId("settings"),
            """{"kind":"settings","id":"settings","modelVersion":4,"nextScreen":2,"idPrefix":"F"}""",
        )
    private val screen =
        FlowDesignBlock.parse(
            BlockId("screen01"),
            """{"kind":"screen","id":"main","name":"Main","code":"F-01","root":{"id":"body","kind":"box"}}""",
        )
    private val original = document(listOf(settings, screen))
    private val request =
        mapOf(
            "kind" to "component",
            "name" to "Button",
            "designId" to "button",
            "props" to listOf("label"),
            "baseVersion" to original.version,
            "markup" to """<button type="button"><span data-bind="label"></span></button>""",
            "opId" to "once-add-button",
        )

    @Test
    fun authoredBlockReturnsItsHashThroughBothInterfaces() {
        every { queries.open(any(), id) } returns DocumentView(original, WebFixtures.space, emptyList())
        every { documents.save(any(), id, original.version, any()) } answers {
            val blocks = arg<List<Block>>(3)
            DocumentWrite(document(blocks), 2, listOf(blocks.last().id), false, false)
        }
        val args = Arguments(json.valueToTree(request + ("document" to "https://docs.example/s/sample/d/$id")))
        val result = FlowDesignAuthorTool(actions).call(args, WebFixtures.webContext)
        assertThat(result.data.toString()).contains("designId=button", "hash=").doesNotContain("data-bind")
        mvc
            .perform(
                post(
                    route,
                    id,
                ).principal(WebFixtures.owner).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.changes[0].designId").value("button"))
            .andDo(
                documented(
                    "flow-design-author",
                    requestFields(
                        fieldWithPath("kind").description("화면 screen, 부품 component, 기존 상자의 자식 layer"),
                        fieldWithPath("name").description("화면 또는 부품의 표시 이름. layer는 생략").optional(),
                        fieldWithPath("designId").description("재사용할 모델 ID. 생략하면 서버에서 발급한다").optional(),
                        fieldWithPath("props").description("컴포넌트에 선언할 글자 입력").optional(),
                        subsectionWithPath("parent").type(JsonFieldType.OBJECT).description("layer를 추가할 상자의 owner와 node").optional(),
                        fieldWithPath("baseVersion").description("마지막으로 읽은 문서의 내용 버전"),
                        fieldWithPath("markup").description("편집 가능한 노드로 변환할 HTML·CSS·SVG 부분집합"),
                        fieldWithPath("opId").description("재시도 시 유지할 요청 ID"),
                    ),
                    responseFields(
                        fieldWithPath("document").description("문서 ID"),
                        fieldWithPath("version").description("내용 버전"),
                        fieldWithPath("revision").description("저장 리비전"),
                        subsectionWithPath("changes").description("바뀐 블록의 서버 ID·모델 ID·종류·해시"),
                        subsectionWithPath("warnings").description("저장 검사의 경고"),
                    ),
                ),
            )
    }

    @Test
    fun unsupportedMarkupReturnsAClientDiagnosticWithoutWriting() {
        every { queries.open(any(), id) } returns DocumentView(original, WebFixtures.space, emptyList())
        val body = json.writeValueAsString(request + ("markup" to "<img src='https://example.invalid/img'></img>"))
        mvc
            .perform(post(route, id).principal(WebFixtures.owner).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun staleAuthorRequestsReturnTheCurrentVersionWithoutTheFullDesign() {
        every { queries.open(any(), id) } returns DocumentView(original, WebFixtures.space, emptyList())
        val old = original.version - 1
        every { documents.save(any(), id, old, any()) } throws StaleRevisionException(old, original.version, original)
        val body = json.writeValueAsString(request + ("baseVersion" to old))
        mvc
            .perform(post(route, id).principal(WebFixtures.owner).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.currentVersion").value(original.version))
            .andExpect(jsonPath("$.currentDocument").doesNotExist())
    }

    private fun document(blocks: List<Block>): Document =
        Document.create(
            id,
            DocumentHeader(WebFixtures.spaceId, WebFixtures.document().header.path, "설계", WebFixtures.properties, "flow"),
            blocks,
            WebFixtures.at,
        )
}
