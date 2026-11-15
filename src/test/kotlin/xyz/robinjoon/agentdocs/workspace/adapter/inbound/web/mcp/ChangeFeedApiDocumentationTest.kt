package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.relaxedResponseFields
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import xyz.robinjoon.agentdocs.application.model.ActivityActor
import xyz.robinjoon.agentdocs.application.model.ActivityEntry
import xyz.robinjoon.agentdocs.application.model.ActivityRecord
import xyz.robinjoon.agentdocs.application.model.ActivitySubject
import xyz.robinjoon.agentdocs.config.McpConfig
import xyz.robinjoon.agentdocs.support.web.RestDocsWebTest
import xyz.robinjoon.agentdocs.support.web.documented
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.agent
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.agentAccess
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.at
import xyz.robinjoon.agentdocs.workspace.application.model.ActivityView
import xyz.robinjoon.agentdocs.workspace.application.model.ChangeFeed
import xyz.robinjoon.agentdocs.workspace.application.port.input.ActivityUseCase

class ChangeFeedApiDocumentationTest : RestDocsWebTest() {
    private val activity = mockk<ActivityUseCase>()
    private val config = McpConfig()
    private val json = JsonMapper.builder().build()
    private val mapper = config.mcpJsonMapper(json)
    private val transport =
        config.mcpTransport(mapper, McpSettings(setOf("https://docs.example.com"))).also {
            config.mcpServer(it, listOf(ChangesSinceTool(activity)), config.mcpToolSpecifications(json, SimpleMeterRegistry()), mapper)
        }
    private val mockMvc = restDocsMockMvc(config.mcpRoutes(transport))
    private val since = at.minusSeconds(1)

    @Test
    fun `MCP continues equal timestamp changes by cursor while keeping since then returns an empty page`() {
        val record = ActivityRecord("document.revised", ActivityActor.system, ActivitySubject(null, null, null, null), "change", at)
        val entries = (1L..101L).map { ActivityView(ActivityEntry(it, record), null, null) }
        every { activity.changesSince(agentAccess, since, 100, null) } returns ChangeFeed(entries.take(100), at, 100, since)
        every { activity.changesSince(agentAccess, since, 100, 100) } returns ChangeFeed(entries.drop(100), at, 101, since)
        every { activity.changesSince(agentAccess, since, 100, 101) } returns ChangeFeed(emptyList(), since, 101, since)

        assertPage(call(null), 100, 100)
        assertPage(
            call(100).andDo(
                documented(
                    "mcp-changes-cursor",
                    relaxedResponseFields(
                        fieldWithPath("result.content[0].text").description(
                            "JSON: initialSince를 다음 since로 유지하고 nextCursor를 cursor로 넘겨 동일시각 변경도 이어 읽는다. " +
                                "nextSince는 레거시 진행용 마지막 스캔 시각이며 빈 페이지는 입력 since. 시각만 쓰면 동일시각 경계에서 누락 가능",
                        ),
                    ),
                ),
            ),
            1,
            101,
        )
        assertPage(call(101), 0, 101)
    }

    private fun assertPage(
        result: ResultActions,
        size: Int,
        cursor: Long,
    ) {
        result.andExpect(status().isOk).andExpect(jsonPath("$.result.isError").value(false)).andExpect {
            val response = json.readTree(it.response.contentAsString)
            val data =
                json.readTree(
                    response
                        .get("result")
                        .get("content")
                        .get(0)
                        .get("text")
                        .stringValue(),
                )
            assertThat(data.get("entries").size()).isEqualTo(size)
            assertThat(data.get("nextCursor").longValue()).isEqualTo(cursor)
            assertThat(data.get("nextSince").stringValue()).isEqualTo((if (size == 0) since else at).toString())
            assertThat(data.get("initialSince").stringValue()).isEqualTo(since.toString())
            if (size == 1) {
                assertThat(
                    data
                        .get("entries")
                        .get(0)
                        .get("id")
                        .longValue(),
                ).isEqualTo(101)
            }
        }
    }

    private fun call(cursor: Long?): ResultActions {
        val continuation = cursor?.let { ",\"cursor\":$it" }.orEmpty()
        val body =
            """{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"changes_since","arguments":{"since":"$since"$continuation}}}"""
        return mockMvc.perform(
            post("/mcp")
                .principal(agent)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
    }
}
