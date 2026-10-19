package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.queryParameters
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import xyz.robinjoon.agentdocs.application.model.ActivityActor
import xyz.robinjoon.agentdocs.application.model.ActivityActorKind
import xyz.robinjoon.agentdocs.application.model.ActivityEntry
import xyz.robinjoon.agentdocs.application.model.ActivityRecord
import xyz.robinjoon.agentdocs.application.model.ActivitySubject
import xyz.robinjoon.agentdocs.support.web.RestDocsWebTest
import xyz.robinjoon.agentdocs.support.web.documented
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.actorFields
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.agent
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.agentAccess
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.at
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.connectionId
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.documentId
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.owner
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.ownerAccess
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.space
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.spaceId
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.spaceRefFields
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.userId
import xyz.robinjoon.agentdocs.workspace.application.model.ActivityView
import xyz.robinjoon.agentdocs.workspace.application.model.ChangeFeed
import xyz.robinjoon.agentdocs.workspace.application.model.UsageReport
import xyz.robinjoon.agentdocs.workspace.application.model.WeeklyUsage
import xyz.robinjoon.agentdocs.workspace.application.port.input.ActivityUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.UsageUseCase
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import java.time.Duration
import java.time.LocalDate

class ActivityApiDocumentationTest : RestDocsWebTest() {
    private val activity = mockk<ActivityUseCase>()
    private val usage = mockk<UsageUseCase>()
    private val mockMvc = restDocsMockMvc(ActivityController(activity, usage), WorkspaceExceptionHandler())
    private val record =
        ActivityRecord(
            "document.revised",
            ActivityActor(ActivityActorKind.AGENT, userId, "robinjoon", connectionId, "Claude Code"),
            ActivitySubject(spaceId.value, documentId.value, null, 4),
            "재시도 설계: 블록 1개 수정",
            at,
        )
    private val view = ActivityView(ActivityEntry(41, record), space, "재시도 설계")

    @Test
    fun `the activity of a space is listed newest first`() {
        every { activity.recent(ownerAccess, SpaceSlug("agent-docs"), 20) } returns listOf(view)

        mockMvc
            .perform(get("/api/activity?space=agent-docs&limit=20").principal(owner))
            .andExpect(status().isOk)
            .andDo(
                documented(
                    "activity-list",
                    queryParameters(
                        parameterWithName("space").optional().description("스페이스 주소. 빠지면 전체 로그로, 소유자만 볼 수 있다"),
                        parameterWithName("limit").optional().description("최대 개수, 기본 50, 최대 200"),
                    ),
                    responseFields(*activityFields("[].")),
                ),
            )
    }

    @Test
    fun `changes since a moment continue from nextSince`() {
        every { activity.changesSince(agentAccess, at.minusSeconds(600), 100) } returns ChangeFeed(listOf(view), at)

        mockMvc
            .perform(get("/api/changes?since={since}", at.minusSeconds(600)).principal(agent))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nextSince").value(at.toString()))
            .andDo(
                documented(
                    "activity-changes",
                    queryParameters(
                        parameterWithName("since").description("이 시각 뒤의 변화만"),
                        parameterWithName("limit").optional().description("최대 개수, 기본 100"),
                    ),
                    responseFields(*activityFields("entries[]."), fieldWithPath("nextSince").description("다음에 since로 넘길 시각")),
                ),
            )
    }

    @Test
    fun `usage metrics are counted per space and week`() {
        every { usage.report(ownerAccess) } returns
            UsageReport(listOf(WeeklyUsage(LocalDate.of(2026, 10, 12), spaceId, 14, 5)), listOf(space), Duration.ofMinutes(42), 3)

        mockMvc
            .perform(get("/api/metrics/usage").principal(owner))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.agentResponseMinutesMedian").value(42))
            .andDo(
                documented(
                    "metrics-usage",
                    responseFields(
                        fieldWithPath("weeks[].weekStart").description("주의 시작 월요일(UTC)"),
                        fieldWithPath("weeks[].spaces[].slug").description("스페이스 주소"),
                        fieldWithPath("weeks[].spaces[].name").description("스페이스 이름"),
                        fieldWithPath("weeks[].spaces[].documents").description("그 주에 쓰거나 고친 문서 수. 한 문서를 여러 번 고쳐도 하나"),
                        fieldWithPath("weeks[].spaces[].comments").description("그 주의 댓글 수"),
                        fieldWithPath(
                            "agentResponseMinutesMedian",
                        ).type(JsonFieldType.NUMBER).optional().description("사람이 연 스레드에 에이전트가 처음 답하기까지 걸린 시간의 중앙값(분)"),
                        fieldWithPath("revisitedDocuments").description("사람이 다른 날 다시 연 문서 수"),
                    ),
                ),
            )
    }

    private fun activityFields(prefix: String): Array<FieldDescriptor> =
        arrayOf(
            fieldWithPath("${prefix}id").description("로그 ID. 늘어나는 순서다"),
            fieldWithPath("${prefix}kind").description("document.revised, thread.opened 같은 종류"),
            *actorFields("${prefix}actor"),
            *spaceRefFields("${prefix}space").map { it.optional() }.toTypedArray(),
            fieldWithPath("${prefix}space").type(JsonFieldType.OBJECT).optional().description("스페이스. 스페이스 밖 활동이면 null"),
            fieldWithPath("${prefix}documentId").type(JsonFieldType.STRING).optional().description("문서 ID"),
            fieldWithPath("${prefix}documentTitle").type(JsonFieldType.STRING).optional().description("문서 제목. 문서가 지워졌으면 null"),
            fieldWithPath("${prefix}threadId").type(JsonFieldType.STRING).optional().description("스레드 ID"),
            fieldWithPath("${prefix}revision").type(JsonFieldType.NUMBER).optional().description("리비전"),
            fieldWithPath("${prefix}summary").description("요약"),
            fieldWithPath("${prefix}at").description("시각"),
        )
}
