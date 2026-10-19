package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.headers.HeaderDocumentation.headerWithName
import org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import xyz.robinjoon.agentdocs.support.web.RestDocsWebTest
import xyz.robinjoon.agentdocs.support.web.documented
import xyz.robinjoon.agentdocs.support.web.problemFields
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.agent
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.agentAccess
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.at
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.owner
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.ownerAccess
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.space
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.spaceFields
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.webContext
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceChange
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceDraft
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceLocator
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceOverview
import xyz.robinjoon.agentdocs.workspace.application.port.input.SpaceNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.input.SpaceNotResolvedException
import xyz.robinjoon.agentdocs.workspace.application.port.input.SpaceUseCase
import xyz.robinjoon.agentdocs.workspace.domain.space.ExternalLink
import xyz.robinjoon.agentdocs.workspace.domain.space.ExternalLinkKind
import xyz.robinjoon.agentdocs.workspace.domain.space.SlugTakenException
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceProfile
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug

// The use case is a strict mock; space rules belong to SpaceTest and SpaceServiceTest. This test checks the HTTP
// shapes, statuses, and error conversion of the space endpoints.
class SpaceApiDocumentationTest : RestDocsWebTest() {
    private val spaces = mockk<SpaceUseCase>()
    private val mockMvc = restDocsMockMvc(SpaceController(spaces), WorkspaceExceptionHandler())

    @Test
    fun `list returns the visible spaces with their activity`() {
        every { spaces.list(ownerAccess) } returns listOf(SpaceOverview(space, 12, at))

        mockMvc
            .perform(get("/api/spaces").principal(owner))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].slug").value("agent-docs"))
            .andDo(
                documented(
                    "spaces-list",
                    responseFields(
                        *spaceFields("[]"),
                        fieldWithPath("[].documentCount").description("휴지통 밖 문서 수"),
                        fieldWithPath("[].lastActivityAt").type(JsonFieldType.STRING).optional().description("마지막 문서 변경 시각"),
                    ),
                ),
            )
    }

    @Test
    fun `create returns the new space and its location`() {
        val profile =
            SpaceProfile.of(
                "Agent Docs",
                "기획과 설계 문서",
                listOf(ExternalLink.of(ExternalLinkKind.GITHUB_REPOSITORY, "homelab/agent-docs")),
            )
        every { spaces.create(webContext, SpaceDraft(SpaceSlug("agent-docs"), profile)) } returns space

        mockMvc
            .perform(
                post("/api/spaces")
                    .principal(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"slug":"agent-docs","name":"Agent Docs","description":"기획과 설계 문서","links":[{"kind":"github_repository","value":"git@github.com:homelab/agent-docs.git"}]}""",
                    ),
            ).andExpect(status().isCreated)
            .andExpect(header().string("Location", "/api/spaces/agent-docs"))
            .andDo(
                documented(
                    "spaces-create",
                    requestFields(
                        fieldWithPath("slug").description("짧은 주소. 소문자, 숫자, 가운데 하이픈으로 1~50자"),
                        fieldWithPath("name").description("이름, 1~100자"),
                        fieldWithPath("description").optional().description("설명, 500자 이하"),
                        fieldWithPath("links").optional().description("외부 연결 20개 이하. git remote를 그대로 넣어도 owner/repo로 정규화한다"),
                        fieldWithPath("links[].kind").description("github_repository, github_organization, local_path, url"),
                        fieldWithPath("links[].value").description("연결 값"),
                    ),
                    responseHeaders(headerWithName("Location").description("새 스페이스 조회 경로")),
                    responseFields(*spaceFields("")),
                ),
            )
    }

    @Test
    fun `get by an old slug answers with the space under its current slug`() {
        val moved = space.moveTo(SpaceSlug("adocs"))
        every { spaces.get(ownerAccess, SpaceSlug("agent-docs")) } returns moved

        mockMvc
            .perform(get("/api/spaces/{slug}", "agent-docs").principal(owner))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.slug").value("adocs"))
            .andExpect(jsonPath("$.formerSlugs[0]").value("agent-docs"))
            .andDo(
                documented(
                    "spaces-get",
                    pathParameters(parameterWithName("slug").description("현재 주소 또는 예전 주소")),
                    responseFields(*spaceFields("")),
                ),
            )
    }

    @Test
    fun `change updates only the given settings`() {
        every { spaces.change(webContext, SpaceSlug("agent-docs"), SpaceChange(SpaceSlug("adocs"), null, "새 설명", null)) } returns
            space.moveTo(SpaceSlug("adocs"))

        mockMvc
            .perform(
                patch("/api/spaces/{slug}", "agent-docs")
                    .principal(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"slug":"adocs","description":"새 설명"}"""),
            ).andExpect(status().isOk)
            .andDo(
                documented(
                    "spaces-change",
                    pathParameters(parameterWithName("slug").description("바꿀 스페이스의 주소")),
                    requestFields(
                        fieldWithPath("slug").optional().description("새 주소. 옛 주소는 새 주소로 넘긴다"),
                        fieldWithPath("name").optional().type(JsonFieldType.STRING).description("새 이름"),
                        fieldWithPath("description").optional().description("새 설명"),
                        fieldWithPath("links").optional().type(JsonFieldType.ARRAY).description("외부 연결 전체를 이 목록으로 바꾼다"),
                    ),
                    responseFields(*spaceFields("")),
                ),
            )
    }

    @Test
    fun `resolve finds the space of an agent's repository`() {
        every {
            spaces.resolve(
                agentAccess,
                SpaceLocator("git@github.com:homelab/agent-docs.git", "/Users/me/IdeaProjects/agent-docs"),
            )
        } returns
            space

        mockMvc
            .perform(
                post("/api/spaces/resolve")
                    .principal(agent)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"remote":"git@github.com:homelab/agent-docs.git","path":"/Users/me/IdeaProjects/agent-docs"}"""),
            ).andExpect(status().isOk)
            .andDo(
                documented(
                    "spaces-resolve",
                    requestFields(
                        fieldWithPath("remote").optional().description("git remote URL. 레포 연결이 org 연결보다 우선한다"),
                        fieldWithPath("path").optional().description("로컬 디렉터리. 가장 깊은 경로 연결이 우선한다"),
                    ),
                    responseFields(*spaceFields("")),
                ),
            )
    }

    @Test
    fun `resolve without a single match lists the candidates`() {
        every { spaces.resolve(agentAccess, SpaceLocator(null, "/tmp/elsewhere")) } throws
            SpaceNotResolvedException("no space you can see is linked to /tmp/elsewhere", listOf(space))

        mockMvc
            .perform(
                post(
                    "/api/spaces/resolve",
                ).principal(agent).contentType(MediaType.APPLICATION_JSON).content("""{"path":"/tmp/elsewhere"}"""),
            ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("not_resolved"))
            .andDo(
                documented(
                    "spaces-not-resolved",
                    responseFields(
                        *workspaceProblemFields(),
                        subsectionWithPath("candidates").description("고를 수 있는 스페이스. 아무것도 맞지 않으면 볼 수 있는 모든 스페이스"),
                    ),
                ),
            )
    }

    @Test
    fun `a taken slug is a conflict and an invisible space is not found`() {
        every { spaces.create(webContext, SpaceDraft(SpaceSlug("docs"), SpaceProfile.of("Docs", "", emptyList()))) } throws
            SlugTakenException(SpaceSlug("docs"))
        every { spaces.get(ownerAccess, SpaceSlug("hidden")) } throws SpaceNotFoundException(SpaceSlug("hidden"))

        mockMvc
            .perform(
                post("/api/spaces").principal(owner).contentType(MediaType.APPLICATION_JSON).content("""{"slug":"docs","name":"Docs"}"""),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("taken"))
            .andDo(documented("spaces-slug-taken", responseFields(*workspaceProblemFields())))
        mockMvc
            .perform(get("/api/spaces/{slug}", "hidden").principal(owner))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("not_found"))
            .andDo(documented("spaces-not-found", responseFields(*workspaceProblemFields())))
    }
}

// Workspace problems add a machine-readable code and, when it helps, a fix.
fun workspaceProblemFields() =
    problemFields() +
        arrayOf(
            fieldWithPath("code").description("not_found, invalid, forbidden, conflict, taken, not_resolved, unauthenticated 중 하나"),
            fieldWithPath("fix").type(JsonFieldType.STRING).optional().description("고치는 법"),
        )
