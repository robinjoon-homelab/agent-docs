package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.restdocs.request.RequestDocumentation.queryParameters
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import xyz.robinjoon.agentdocs.support.web.RestDocsWebTest
import xyz.robinjoon.agentdocs.support.web.documented
import xyz.robinjoon.agentdocs.support.web.problemFields
import xyz.robinjoon.agentdocs.workspace.application.model.EditMeasure
import xyz.robinjoon.agentdocs.workspace.application.model.EditStatistics
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.input.EditSessionUseCase
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.editsession.EditEndReason
import xyz.robinjoon.agentdocs.workspace.domain.editsession.EditSession
import xyz.robinjoon.agentdocs.workspace.domain.editsession.EditSessionTarget
import java.util.UUID

class EditStatisticsApiDocumentationTest : RestDocsWebTest() {
    private val sessions = mockk<EditSessionUseCase>()
    private val mvc = restDocsMockMvc(EditStatisticsController(sessions), EditStatisticsExceptionHandler())
    private val id = DocumentId(UUID.fromString("55555555-5555-4555-8555-555555555555"))
    private val target = EditSessionTarget(id, WebFixtures.spaceId, WebFixtures.agentActor, null)
    private val opened = EditSession.start(target, WebFixtures.at)

    @Test
    fun `statistics response separates human explicit estimated and open intervals`() {
        every { sessions.statistics(WebFixtures.ownerAccess, id) } returns
            EditStatistics(EditMeasure(10_000, 1), EditMeasure(20_000, 2), EditMeasure(3_000, 1), 1)
        mvc
            .perform(get("/api/documents/{id}/edit-statistics", id.value).principal(WebFixtures.owner))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.human.milliseconds").value(10_000))
            .andDo(
                documented(
                    "edit-statistics-get",
                    pathParameters(parameterWithName("id").description("문서 ID")),
                    responseFields(
                        fieldWithPath("human.milliseconds").description("사람의 닫힌 구간 합집합, 밀리초"),
                        fieldWithPath("human.count").description("겹침을 합친 사람 구간 수"),
                        fieldWithPath("agentExplicit.milliseconds").description("명시 종료한 에이전트 소요 시간, 밀리초"),
                        fieldWithPath("agentExplicit.count").description("명시 종료 건수"),
                        fieldWithPath("agentEstimated.milliseconds").description("추정 종료한 에이전트 소요 시간, 밀리초"),
                        fieldWithPath("agentEstimated.count").description("추정 종료 건수"),
                        fieldWithPath("openCount").description("열린 구간 수, 시간 합계에서 제외"),
                    ),
                ),
            )
    }

    @Test
    fun `close carries the last successfully adopted input watermark and needs no completion button`() {
        val tab = UUID.randomUUID()
        every { sessions.closeHuman(WebFixtures.ownerAccess, id, tab, WebFixtures.at) } returns null
        mvc
            .perform(
                post("/api/documents/{id}/edit-statistics/close", id.value)
                    .principal(WebFixtures.owner)
                    .queryParam("tabId", tab.toString())
                    .queryParam("lastInputAt", WebFixtures.at.toString()),
            ).andExpect(status().isOk)
            .andDo(
                documented(
                    "edit-statistics-close",
                    pathParameters(parameterWithName("id").description("문서 ID")),
                    queryParameters(
                        parameterWithName("tabId").description("입력한 탭 ID"),
                        parameterWithName("lastInputAt").description("이 탭이 마지막으로 저장에 성공한 실제 입력 시각"),
                    ),
                ),
            )
    }

    @Test
    fun `start returns a statistics session without modifying a document`() {
        every { sessions.start(any(), id) } returns opened
        mvc
            .perform(
                post("/api/documents/{id}/edit-statistics/start", id.value)
                    .principal(WebFixtures.agent)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"opId":"start-1"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(opened.id.toString()))
            .andDo(
                documented(
                    "edit-statistics-start",
                    pathParameters(parameterWithName("id").description("문서 ID")),
                    requestFields(fieldWithPath("opId").description("통계 시작 멱등 키")),
                    responseFields(*sessionFields()),
                ),
            )
    }

    @Test
    fun `end returns an immutable explicit or estimated result`() {
        every { sessions.end(any(), opened.id) } returns opened.close(WebFixtures.at.plusSeconds(10), EditEndReason.EXPLICIT)
        mvc
            .perform(
                post("/api/edit-sessions/{id}/end", opened.id)
                    .principal(WebFixtures.agent)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"opId":"end-1"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.reason").value("explicit"))
            .andDo(
                documented(
                    "edit-statistics-end",
                    pathParameters(parameterWithName("id").description("통계 구간 ID")),
                    requestFields(fieldWithPath("opId").description("통계 종료 멱등 키")),
                    responseFields(*sessionFields()),
                ),
            )
    }

    @Test
    fun `a document outside the caller scope is not found`() {
        every { sessions.statistics(WebFixtures.ownerAccess, id) } throws DocumentNotFoundException(id)
        mvc
            .perform(get("/api/documents/{id}/edit-statistics", id.value).principal(WebFixtures.owner))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("not_found"))
            .andDo(
                documented(
                    "edit-statistics-not-found",
                    responseFields(*problemFields(), fieldWithPath("code").description("오류 코드"), fieldWithPath("fix").description("수정 안내")),
                ),
            )
    }

    private fun sessionFields() =
        arrayOf(
            fieldWithPath("id").description("편집 시간 구간 ID"),
            fieldWithPath("documentId").description("문서 ID"),
            fieldWithPath("startedAt").description("시작 시각"),
            fieldWithPath("lastActivityAt").description("마지막 편집 또는 통계 요청 시각"),
            fieldWithPath("endedAt").type(JsonFieldType.STRING).optional().description("종료 시각, 열린 구간이면 null"),
            fieldWithPath("reason").type(JsonFieldType.STRING).optional().description("explicit 또는 estimated, 열린 구간이면 null"),
        )
}
