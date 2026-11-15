package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import xyz.robinjoon.agentdocs.support.web.RestDocsWebTest
import xyz.robinjoon.agentdocs.support.web.documented
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.document
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.documentId
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.owner
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.space
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.webContext
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentTransfer
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentWrite
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceReference
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentTransferUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.StaleRevisionException
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath

class DocumentTransferApiDocumentationTest : RestDocsWebTest() {
    private val transfers = mockk<DocumentTransferUseCase>()
    private val statistics = mockk<EditStatisticsRecorder>()
    private val mvc = restDocsMockMvc(DocumentTransferController(transfers, statistics), WorkspaceExceptionHandler())
    private val request = DocumentTransfer(space.slug, DocumentPath("moved"), 4)

    @Test
    fun `transfer returns the captured destination and related tree changes`() {
        val current = document(revision = 4)
        val moved = current.transfer(space.id, request.path, WebFixtures.at)
        every {
            transfers.transfer(
                webContext,
                documentId,
                request,
            )
        } returns
            DocumentWrite(
                moved,
                moved.revision,
                emptyList(),
                false,
                false,
                space = SpaceReference(space.id, space.slug, space.profile.name),
            )
        every { statistics.agent(WebFixtures.ownerAccess, documentId) } returns true
        mvc
            .perform(
                post("/api/documents/{id}/transfer", documentId)
                    .principal(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"space":"${space.slug}","path":"moved","baseVersion":4}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.path").value("moved"))
            .andExpect(jsonPath("$.version").value(5))
            .andDo(
                documented(
                    "documents-transfer",
                    pathParameters(parameterWithName("id").description("이사할 문서 ID")),
                    requestFields(
                        fieldWithPath("space").description("목적 스페이스 slug"),
                        fieldWithPath("path").description("목적 전체 경로"),
                        fieldWithPath("baseVersion").description("마지막으로 읽은 version"),
                        fieldWithPath(
                            "opId",
                        ).type(org.springframework.restdocs.payload.JsonFieldType.STRING).optional().description("최초 결과 재생용 키"),
                    ),
                    responseFields(
                        fieldWithPath("documentId").description("보존되는 문서 ID"),
                        subsectionWithPath("space").description("최초 결과의 목적 스페이스"),
                        fieldWithPath("path").description("목적 경로"),
                        fieldWithPath("version").description("새 version"),
                        fieldWithPath("revision").description("확정 revision"),
                        fieldWithPath("unchanged").description("실제 변경 없음"),
                        subsectionWithPath("alsoChanged").description("함께 이동한 하위와 생성한 목적 상위의 ID·경로·제목·version·revision·change"),
                    ),
                ),
            )
    }

    @Test
    fun `missing baseVersion is rejected and stale versions preserve conflict details`() {
        mvc
            .perform(
                post("/api/documents/{id}/transfer", documentId)
                    .principal(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"space":"${space.slug}","path":"moved"}"""),
            ).andExpect(status().isBadRequest)
        every { transfers.transfer(webContext, documentId, request) } throws StaleRevisionException(4, 5, document(revision = 5))
        mvc
            .perform(
                post("/api/documents/{id}/transfer", documentId)
                    .principal(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"space":"${space.slug}","path":"moved","baseVersion":4}"""),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.currentVersion").value(5))
    }
}
