package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import xyz.robinjoon.agentdocs.support.web.RestDocsWebTest
import xyz.robinjoon.agentdocs.support.web.documented
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.document
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.documentId
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.owner
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.webContext
import xyz.robinjoon.agentdocs.workspace.application.model.BlockRestore
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentWrite
import xyz.robinjoon.agentdocs.workspace.application.model.RevisionLabel
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentQueryUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentUseCase
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId

class RevisionCommandsApiDocumentationTest : RestDocsWebTest() {
    private val documents = mockk<DocumentUseCase>()
    private val mockMvc =
        restDocsMockMvc(HistoryController(mockk<DocumentQueryUseCase>(), documents, mockk(relaxed = true)), WorkspaceExceptionHandler())

    @Test
    fun `a revision name can be removed with an empty string`() {
        every { documents.nameRevision(webContext, documentId, 2, "") } returns RevisionLabel(documentId, 2, null)
        mockMvc
            .perform(
                post("/api/documents/{id}/revisions/{number}/name", documentId, 2)
                    .principal(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":""}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.revision").value(2))
            .andDo(
                documented(
                    "history-name",
                    pathParameters(parameterWithName("id").description("문서 ID"), parameterWithName("number").description("확정 리비전")),
                    requestFields(
                        fieldWithPath("name").description("최대 100자 이름, 빈 문자열은 제거"),
                        fieldWithPath(
                            "opId",
                        ).type(org.springframework.restdocs.payload.JsonFieldType.STRING).optional().description("최초 결과를 재생할 멱등 키"),
                    ),
                    responseFields(
                        fieldWithPath("documentId").description("문서 ID"),
                        fieldWithPath("revision").description("대상 리비전"),
                        fieldWithPath("name").type(JsonFieldType.STRING).optional().description("저장한 이름, 제거했으면 null"),
                    ),
                ),
            )
    }

    @Test
    fun `one block restore requires a base version and returns the new version`() {
        val block = BlockId("block1")
        every { documents.restoreBlock(webContext, documentId, BlockRestore(2, block, 4)) } returns
            DocumentWrite(document(revision = 5), 5, listOf(block), false, false)
        mockMvc
            .perform(
                post("/api/documents/{id}/restore-block", documentId)
                    .principal(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"revision":2,"blockId":"block1","baseVersion":4}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.revision").value(5))
            .andExpect(jsonPath("$.changedBlockIds[0]").value("block1"))
            .andDo(
                documented(
                    "history-restore-block",
                    pathParameters(parameterWithName("id").description("문서 ID")),
                    requestFields(
                        fieldWithPath("revision").description("원본 확정 리비전"),
                        fieldWithPath("blockId").description("원본 블록 ID"),
                        fieldWithPath("baseVersion").description("현재 문서 version"),
                        fieldWithPath(
                            "opId",
                        ).type(org.springframework.restdocs.payload.JsonFieldType.STRING).optional().description("멱등 키"),
                        *WebFixtures.writeMetadataFields(),
                    ),
                    responseFields(
                        fieldWithPath("version").description("새 문서 version"),
                        fieldWithPath("revision").description("새 확정 리비전"),
                        fieldWithPath("changedBlockIds").description("복원한 블록"),
                    ),
                ),
            )
    }

    @Test
    fun `a block restore without baseVersion is rejected`() {
        mockMvc
            .perform(
                post("/api/documents/{id}/restore-block", documentId)
                    .principal(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"revision":2,"blockId":"block1"}"""),
            ).andExpect(status().isBadRequest)
    }
}
