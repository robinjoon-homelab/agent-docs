package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.relaxedResponseFields
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import xyz.robinjoon.agentdocs.support.web.RestDocsWebTest
import xyz.robinjoon.agentdocs.support.web.documented
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentDraft
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentView
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentWrite
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentQueryUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.SpaceUseCase
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentHeader
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowRules

class FlowApiDocumentationTest : RestDocsWebTest() {
    private val documents = mockk<DocumentUseCase>()
    private val queries = mockk<DocumentQueryUseCase>()
    private val spaces = mockk<SpaceUseCase>()
    private val recorder = mockk<EditStatisticsRecorder>(relaxed = true)
    private val mvc = restDocsMockMvc(DocumentController(documents, queries, spaces, recorder), WorkspaceExceptionHandler())

    @Test
    fun `create a version four flow with server block ids and model ids`() {
        every { documents.create(any(), WebFixtures.space.slug, any()) } answers {
            val draft = thirdArg<DocumentDraft>()
            val header = DocumentHeader(WebFixtures.spaceId, draft.path, draft.title, draft.properties, draft.type)
            val doc = Document.create(WebFixtures.documentId, header, draft.blocks, WebFixtures.at)
            DocumentWrite(doc, 1, doc.blocks.map { it.id }, false, false)
        }
        every { queries.open(any(), any()) } returns DocumentView(WebFixtures.document(), WebFixtures.space, emptyList())
        val input =
            """
            {"path":"version-four","title":"메모 화면","type":"flow","blocks":[
              {"id":"outerconfig","type":"flowDesign","design":{"kind":"settings","id":"config","modelVersion":4,"frame":"phone","idPrefix":"M","nextScreen":2}},
              {"id":"outerscreen","type":"flowDesign","design":{"kind":"screen","id":"memo","code":"M-01","name":"메모","root":{"id":"root","kind":"box","layout":{"mode":"column"},"children":[]}}}]}
            """.trimIndent()
        mvc
            .perform(
                post("/api/spaces/{slug}/documents", "agent-docs")
                    .principal(WebFixtures.owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(input),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.blocks[1].id").value("outerscreen"))
            .andExpect(jsonPath("$.blocks[1].design.id").value("memo"))
            .andExpect(jsonPath("$.blocks[1].hash").isString)
            .andDo(
                documented(
                    "flow-design-create",
                    requestFields(
                        fieldWithPath("path").description("스페이스 안의 문서 경로"),
                        fieldWithPath("title").description("문서 제목"),
                        fieldWithPath("type").description("화면 흐름 문서는 flow"),
                        fieldWithPath("blocks[].id").description("댓글·리비전·충돌 검사가 가리키는 블록 ID"),
                        fieldWithPath("blocks[].type").description("v4 블록은 flowDesign"),
                        subsectionWithPath("blocks[].design").description("모델의 id·kind와 해당 종류의 필드. 첫 블록은 modelVersion 4인 설정"),
                    ),
                    relaxedResponseFields(subsectionWithPath("blocks").description("정규화한 디자인 블록, 외부 블록 ID와 해시")),
                ),
            )
    }

    @Test
    fun `create a flow with the default screen and an empty prefix`() {
        every { documents.create(any(), WebFixtures.space.slug, any()) } answers {
            val draft = thirdArg<DocumentDraft>()
            val doc =
                Document.create(
                    WebFixtures.documentId,
                    DocumentHeader(WebFixtures.spaceId, draft.path, draft.title, draft.properties, draft.type),
                    draft.blocks,
                    WebFixtures.at,
                )
            DocumentWrite(doc, 1, doc.blocks.map { it.id }, false, false)
        }
        val original = WebFixtures.document()
        val flow = Document.create(original.id, original.header.copy(type = "flow"), FlowRules.initial("phone", ""), WebFixtures.at)
        every { queries.open(any(), any()) } returns DocumentView(flow, WebFixtures.space, emptyList())
        mvc
            .perform(
                post("/api/spaces/{slug}/documents", "agent-docs")
                    .principal(WebFixtures.owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"path":"flow","title":"화면 흐름","type":"flow","frame":"phone","idPrefix":""}"""),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.type").value("flow"))
            .andExpect(jsonPath("$.blocks[0].type").value("flowSettings"))
            .andExpect(jsonPath("$.blocks[0].idPrefix").value(""))
            .andExpect(jsonPath("$.blocks[1].screenId").value("S-01"))
            .andDo(
                documented(
                    "flow-documents-create",
                    requestFields(
                        fieldWithPath("path").description("문서 경로"),
                        fieldWithPath("title").description("제목"),
                        fieldWithPath("type").description("flow. 기본은 doc이며 생성 뒤 바꿀 수 없다"),
                        fieldWithPath("frame").description("기본 틀. 기본 phone, custom 불가"),
                        fieldWithPath("idPrefix").description("대문자·숫자 0~4자. 빈 값은 새 화면 ID에 S를 쓴다"),
                    ),
                    relaxedResponseFields(
                        fieldWithPath("type").description("flow"),
                        subsectionWithPath("blocks").description("flowSettings 하나, flowScreen과 그 화면의 flowElement 순서"),
                    ),
                ),
            )
    }

    @Test
    fun `an unsupported default frame is rejected as a bad request`() {
        mvc
            .perform(
                post("/api/spaces/{slug}/documents", "agent-docs")
                    .principal(WebFixtures.owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"path":"flow","title":"Flow","type":"flow","frame":"custom"}"""),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `invalid custom frame numbers in flow edit markdown return 400`() {
        val original = WebFixtures.document()
        val blocks = FlowRules.initial("phone", "M")
        val flow = Document.create(original.id, original.header.copy(type = "flow"), blocks, WebFixtures.at)
        every { queries.open(any(), flow.id) } returns DocumentView(flow, WebFixtures.space, emptyList())
        val markdown = "## M-01 Name\n- entry:\n- frame: custom\n- frameW: invalid\n- frameH: 800"
        val input =
            mapOf(
                "operations" to
                    listOf(
                        mapOf(
                            "op" to "replace",
                            "blockId" to blocks[1].id.value,
                            "ifHash" to
                                xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown
                                    .hash(blocks[1]),
                            "markdown" to markdown,
                        ),
                    ),
            )
        mvc
            .perform(
                post("/api/documents/{id}/edits", flow.id)
                    .principal(WebFixtures.owner)
                    .contentType(
                        MediaType.APPLICATION_JSON,
                    ).content(
                        tools.jackson.databind.json.JsonMapper
                            .builder()
                            .build()
                            .writeValueAsString(input),
                    ),
            ).andExpect(status().isBadRequest)
    }
}
