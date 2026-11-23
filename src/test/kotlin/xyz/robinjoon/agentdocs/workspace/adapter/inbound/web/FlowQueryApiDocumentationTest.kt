package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.queryParameters
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import xyz.robinjoon.agentdocs.support.web.RestDocsWebTest
import xyz.robinjoon.agentdocs.support.web.documented
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.document
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.documentId
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.owner
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.ownerAccess
import xyz.robinjoon.agentdocs.workspace.application.model.FlowQuery
import xyz.robinjoon.agentdocs.workspace.application.model.MarkdownChunk
import xyz.robinjoon.agentdocs.workspace.application.model.MarkdownFormat
import xyz.robinjoon.agentdocs.workspace.application.model.MarkdownRequest
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentQueryUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.EditSessionUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.SpaceUseCase

// The REST form of docs_read's flowQuery: the same canonical candidate lines in the usual markdown response.
class FlowQueryApiDocumentationTest : RestDocsWebTest() {
    private val queries = mockk<DocumentQueryUseCase>()
    private val spaces = mockk<SpaceUseCase> { every { agentGuide(any(), any()) } returns null }
    private val mockMvc =
        restDocsMockMvc(
            DocumentController(
                mockk<DocumentUseCase>(),
                queries,
                spaces,
                EditStatisticsRecorder(mockk<EditSessionUseCase>(relaxed = true)),
            ),
            WorkspaceExceptionHandler(),
        )

    @Test
    fun `flow query parameters list candidate screens and elements with ids hashes and links`() {
        val listing =
            "flowQuery: 1–1 of 1 matches (text \"저장\", kind button) at revision 3\n" +
                "- element 저장 · button 285×42 · in M-01 새 메모 작성 (b:screen1) · 설명 있음 · → M-05 · match: label " +
                "(b:save001 h:61174ab4) https://docs.example/s/my-space/d/$documentId?flowScreen=screen1&flowElement=save001\n"
        val request =
            MarkdownRequest(MarkdownFormat.OUTLINE, null, null, 0, null, FlowQuery(text = "저장", kind = "button", limit = 20, offset = 0))
        every { queries.markdown(ownerAccess, documentId, request) } returns MarkdownChunk(document(), listing, null, 1)

        mockMvc
            .perform(get("/api/documents/{id}/markdown?flowText=저장&flowKind=button&flowLimit=20&flowOffset=0", documentId).principal(owner))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.markdown").value(listing))
            .andExpect(jsonPath("$.totalBlocks").value(1))
            .andDo(
                documented(
                    "documents-markdown-flow-query",
                    queryParameters(
                        parameterWithName("flowText").optional().description("화면 ID·이름·진입·설명, 요소 이름·값·항목·상태·이동·설명에서 찾을 글자(대소문자 무시)"),
                        parameterWithName("flowKind").optional().description("screen, element 또는 요소 종류(button 등). 요소 종류면 그 종류만 돌려준다"),
                        parameterWithName("flowScreen").optional().description("화면 ID(M-01) 또는 화면 블록 ID. 그 화면과 소속 요소만"),
                        parameterWithName("flowLimit").optional().description("한 번에 돌려줄 후보 수, 1~100(기본 20)"),
                        parameterWithName("flowOffset").optional().description("건너뛸 후보 수. 마지막 줄이 다음 offset을 알려 준다"),
                    ),
                    responseFields(
                        fieldWithPath("documentId").description("문서 ID"),
                        fieldWithPath("version").description("현재 내용 버전"),
                        fieldWithPath("revision").description("마지막 확정 리비전"),
                        fieldWithPath("markdown").description("후보 목록. 첫 줄은 일치 수와 조건, 후보마다 블록 ID·해시·소속 화면·웹 링크"),
                        fieldWithPath("agentGuide").type(JsonFieldType.OBJECT).optional().description("스페이스 에이전트 가이드"),
                        fieldWithPath("nextCursor").type(JsonFieldType.NUMBER).optional().description("후보 목록이 길면 다음 cursor"),
                        fieldWithPath("contiguous").description("페이지를 구분자 없이 이어 붙이면 원본이 됨"),
                        fieldWithPath("totalBlocks").description("조건에 맞는 후보 수"),
                    ),
                ),
            )
    }

    @Test
    fun `a flow query with another format is refused`() {
        mockMvc
            .perform(get("/api/documents/{id}/markdown?format=markdown&flowText=저장", documentId).principal(owner))
            .andExpect(status().isBadRequest)
    }
}
