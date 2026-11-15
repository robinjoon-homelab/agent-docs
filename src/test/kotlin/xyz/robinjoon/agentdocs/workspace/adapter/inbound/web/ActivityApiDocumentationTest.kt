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
import tools.jackson.databind.json.JsonMapper
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
import xyz.robinjoon.agentdocs.workspace.application.port.input.ActivityUseCase
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug

class ActivityApiDocumentationTest : RestDocsWebTest() {
    private val activity = mockk<ActivityUseCase>()
    private val mockMvc = restDocsMockMvc(ActivityController(activity), WorkspaceExceptionHandler())
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
    fun `changes keep the original since and continue by unique cursor`() {
        every { activity.changesSince(agentAccess, at.minusSeconds(600), 100) } returns
            ChangeFeed(listOf(view), at, 41, at.minusSeconds(600))

        mockMvc
            .perform(get("/api/changes?since={since}", at.minusSeconds(600)).principal(agent))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nextSince").value(at.toString()))
            .andExpect(jsonPath("$.initialSince").value(at.minusSeconds(600).toString()))
            .andDo(
                documented(
                    "activity-changes",
                    queryParameters(
                        parameterWithName("since").description("이 시각 뒤의 변화만. 커서로 이어 읽을 때 응답 initialSince를 그대로 사용"),
                        parameterWithName("cursor").optional().description("직전 응답 nextCursor. 이 ID 다음부터 이어 읽는다"),
                        parameterWithName("limit").optional().description("최대 개수, 기본 100"),
                    ),
                    responseFields(
                        *activityFields("entries[]."),
                        fieldWithPath("nextSince").description("레거시 시각 기반 진행용 마지막 스캔 이벤트 시각. 빈 페이지는 입력 since. 동일시각 경계에서 누락 가능"),
                        fieldWithPath("initialSince").description("이번 조회의 입력 since. 커서로 이어 읽을 때 이 값을 since로 유지"),
                        fieldWithPath(
                            "nextCursor",
                        ).type(JsonFieldType.NUMBER).optional().description("다음 요청 cursor. 동일시각 변경도 누락 없이 이어 읽는다"),
                    ),
                ),
            )
    }

    @Test
    fun `cursor continues from the hundredth equal timestamp entry then preserves the cursor on an empty page`() {
        val since = at.minusSeconds(1)
        val first = (1L..100L).map { ActivityView(ActivityEntry(it, record), space, "재시도 설계") }
        val last = ActivityView(ActivityEntry(101, record), space, "재시도 설계")
        every { activity.changesSince(agentAccess, since, 100, null) } returns ChangeFeed(first, at, 100, since)
        every { activity.changesSince(agentAccess, since, 100, 100) } returns ChangeFeed(listOf(last), at, 101, since)
        every { activity.changesSince(agentAccess, since, 100, 101) } returns ChangeFeed(emptyList(), since, 101, since)

        mockMvc
            .perform(get("/api/changes").param("since", since.toString()).principal(agent))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.entries.length()").value(100))
            .andExpect(jsonPath("$.nextCursor").value(100))
            .andExpect(jsonPath("$.nextSince").value(at.toString()))
            .andExpect(jsonPath("$.initialSince").value(since.toString()))
        mockMvc
            .perform(get("/api/changes").param("since", since.toString()).param("cursor", "100").principal(agent))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.entries[0].id").value(101))
            .andExpect(jsonPath("$.nextCursor").value(101))
            .andExpect(jsonPath("$.nextSince").value(at.toString()))
            .andExpect(jsonPath("$.initialSince").value(since.toString()))
        mockMvc
            .perform(get("/api/changes").param("since", since.toString()).param("cursor", "101").principal(agent))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.entries").isEmpty)
            .andExpect(jsonPath("$.nextCursor").value(101))
            .andExpect(jsonPath("$.nextSince").value(since.toString()))
            .andExpect(jsonPath("$.initialSince").value(since.toString()))
    }

    @Test
    fun `legacy REST consumers continue from nextSince without replaying the page`() {
        val since = at.minusSeconds(10)
        every { activity.changesSince(agentAccess, since, 100, null) } returns ChangeFeed(listOf(view), at, 41, since)
        every { activity.changesSince(agentAccess, at, 100, null) } returns ChangeFeed(emptyList(), at, null, at)

        val first =
            mockMvc
                .perform(get("/api/changes").param("since", since.toString()).principal(agent))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.entries[0].id").value(41))
                .andExpect(jsonPath("$.nextSince").value(at.toString()))
                .andReturn()
        val nextSince =
            JsonMapper
                .builder()
                .build()
                .readTree(first.response.contentAsString)
                .get("nextSince")
                .stringValue()
        mockMvc
            .perform(get("/api/changes").param("since", nextSince).principal(agent))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.entries").isEmpty)
            .andExpect(jsonPath("$.nextSince").value(at.toString()))
            .andExpect(jsonPath("$.initialSince").value(at.toString()))
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
