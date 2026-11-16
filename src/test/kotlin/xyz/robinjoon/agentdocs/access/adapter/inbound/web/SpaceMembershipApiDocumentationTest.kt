package xyz.robinjoon.agentdocs.access.adapter.inbound.web

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.restdocs.request.RequestDocumentation.queryParameters
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import xyz.robinjoon.agentdocs.access.application.model.Caller
import xyz.robinjoon.agentdocs.access.application.model.CallerAgent
import xyz.robinjoon.agentdocs.access.application.model.CallerScope
import xyz.robinjoon.agentdocs.access.application.model.MembershipInvite
import xyz.robinjoon.agentdocs.access.application.model.MembershipView
import xyz.robinjoon.agentdocs.access.application.port.input.OwnerOnlyException
import xyz.robinjoon.agentdocs.access.application.port.input.SpaceMembershipNotFoundException
import xyz.robinjoon.agentdocs.access.application.port.input.SpaceMembershipUseCase
import xyz.robinjoon.agentdocs.access.domain.GitHubLogin
import xyz.robinjoon.agentdocs.access.domain.InvalidAccessException
import xyz.robinjoon.agentdocs.access.domain.membership.MembershipState
import xyz.robinjoon.agentdocs.access.domain.membership.MembershipTakenException
import xyz.robinjoon.agentdocs.access.domain.membership.SpaceInvitation
import xyz.robinjoon.agentdocs.access.domain.membership.SpaceMembership
import xyz.robinjoon.agentdocs.access.domain.membership.SpaceMembershipId
import xyz.robinjoon.agentdocs.access.domain.membership.SpaceRole
import xyz.robinjoon.agentdocs.access.domain.user.UserId
import xyz.robinjoon.agentdocs.support.web.RestDocsWebTest
import xyz.robinjoon.agentdocs.support.web.documented
import xyz.robinjoon.agentdocs.support.web.problemFields
import java.time.Instant
import java.util.UUID

class SpaceMembershipApiDocumentationTest : RestDocsWebTest() {
    private val memberships = mockk<SpaceMembershipUseCase>()
    private val mockMvc = restDocsMockMvc(SpaceMembershipController(memberships), AccessExceptionHandler())
    private val userId = UUID.fromString("11111111-1111-4111-8111-111111111111")
    private val ownerCaller = Caller(userId, "robinjoon", true, null, null, CallerScope.OWNER)
    private val owner = CallerAuthentication(ownerCaller)
    private val agent =
        CallerAuthentication(
            Caller(userId, "robinjoon", false, CallerAgent(UUID.randomUUID(), "Claude Code"), emptyMap(), CallerScope.READ),
        )
    private val space = UUID.fromString("33333333-3333-4333-8333-333333333333")
    private val memberId = UUID.fromString("77777777-7777-4777-8777-777777777777")
    private val at = Instant.parse("2026-11-16T03:00:00Z")
    private val waiting = membership(UUID.fromString("88888888-8888-4888-8888-888888888888"), "carol", null)
    private val joined = membership(UUID.fromString("99999999-9999-4999-8999-999999999999"), "bob", memberId)

    private fun membership(
        id: UUID,
        login: String,
        member: UUID?,
    ): SpaceMembership =
        SpaceMembership.restore(
            SpaceMembershipId(id),
            SpaceInvitation(space, GitHubLogin(login), UserId(userId), at),
            MembershipState(SpaceRole.EDITOR, member?.let(::UserId), member?.let { at.plusSeconds(600) }, null),
        )

    @Test
    fun `the owner lists the live members of a space`() {
        every { memberships.list(ownerCaller, space) } returns
            listOf(MembershipView(joined, "bob-renamed"), MembershipView(waiting, "carol"))

        mockMvc
            .perform(get("/api/memberships").queryParam("space", space.toString()).principal(owner))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].state").value("active"))
            .andExpect(jsonPath("$[0].login").value("bob-renamed"))
            .andExpect(jsonPath("$[0].avatarUrl").value("https://github.com/bob-renamed.png"))
            .andExpect(jsonPath("$[1].state").value("pending"))
            .andDo(
                documented(
                    "memberships-list",
                    queryParameters(parameterWithName("space").description("스페이스 ID")),
                    responseFields(*membershipFields("[].")),
                ),
            )
    }

    @Test
    fun `inviting a login lets it sign in and waits for its first sign-in`() {
        every { memberships.invite(ownerCaller, MembershipInvite(space, "carol", SpaceRole.EDITOR)) } returns
            MembershipView(waiting, "carol")

        mockMvc
            .perform(
                post("/api/memberships")
                    .principal(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"space":"$space","login":"carol","role":"editor"}"""),
            ).andExpect(status().isCreated)
            .andExpect(header().string("Location", "/api/memberships/${waiting.id}"))
            .andExpect(jsonPath("$.state").value("pending"))
            .andDo(
                documented(
                    "memberships-invite",
                    requestFields(
                        fieldWithPath("space").description("스페이스 ID. 존재를 확인하지 않으므로 화면은 스페이스 목록에서 고른다"),
                        fieldWithPath("login").description(
                            "GitHub 로그인. 1~39자 영문·숫자이고 하이픈은 글자 사이에 하나씩만 쓴다(처음·끝·연속 하이픈은 400 invalid). 대소문자를 구분하지 않고 비교한다",
                        ),
                        fieldWithPath("role").description("editor 또는 commenter"),
                    ),
                    responseFields(*membershipFields("")),
                ),
            )
    }

    @Test
    fun `changing the role answers with the membership and revoking keeps the record`() {
        every { memberships.changeRole(ownerCaller, joined.id, SpaceRole.COMMENTER) } returns
            MembershipView(requireNotNull(joined.changeRole(SpaceRole.COMMENTER)), "bob")
        every { memberships.revoke(ownerCaller, joined.id) } just runs

        mockMvc
            .perform(
                patch("/api/memberships/{id}", joined.id.value)
                    .principal(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"role":"commenter"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.role").value("commenter"))
            .andDo(
                documented(
                    "memberships-change-role",
                    pathParameters(parameterWithName("id").description("멤버십 ID")),
                    requestFields(fieldWithPath("role").description("editor 또는 commenter. 그 사람의 다음 요청부터 적용된다")),
                    responseFields(*membershipFields("")),
                ),
            )
        mockMvc
            .perform(delete("/api/memberships/{id}", joined.id.value).principal(owner))
            .andExpect(status().isNoContent)
            .andDo(documented("memberships-revoke", pathParameters(parameterWithName("id").description("멤버십 ID. 이미 회수했어도 204"))))
    }

    @Test
    fun `a login already a member of the space names the existing membership`() {
        every { memberships.invite(ownerCaller, MembershipInvite(space, "bob", SpaceRole.COMMENTER)) } throws
            MembershipTakenException(joined.id)

        mockMvc
            .perform(
                post("/api/memberships")
                    .principal(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"space":"$space","login":"bob","role":"commenter"}"""),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("conflict"))
            .andExpect(jsonPath("$.membershipId").value(joined.id.value.toString()))
            .andDo(
                documented(
                    "memberships-conflict",
                    responseFields(
                        *problemFields(),
                        fieldWithPath("code").description("conflict"),
                        fieldWithPath("fix").description("기존 멤버십의 역할을 바꾸라는 안내"),
                        fieldWithPath("membershipId").type(JsonFieldType.STRING).optional().description("겹친 살아 있는 멤버십 ID"),
                    ),
                ),
            )
    }

    @Test
    fun `malformed requests, other callers, and unknown memberships get their problems`() {
        every { memberships.list(any(), space) } throws OwnerOnlyException("listing members")
        every { memberships.changeRole(ownerCaller, waiting.id, SpaceRole.EDITOR) } throws SpaceMembershipNotFoundException(waiting.id)
        every { memberships.invite(ownerCaller, MembershipInvite(space, "robinjoon", SpaceRole.EDITOR)) } throws
            InvalidAccessException("the owner already sees every space")

        mockMvc.perform(get("/api/memberships").queryParam("space", space.toString())).andExpect(status().isUnauthorized)
        mockMvc
            .perform(get("/api/memberships").queryParam("space", space.toString()).principal(agent))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("forbidden"))
        mockMvc
            .perform(get("/api/memberships").queryParam("space", "not-a-space").principal(owner))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("invalid"))
        invalidInvite("""{"space":"$space","login":"carol","role":"owner"}""")
        invalidInvite("""{"login":"carol","role":"editor"}""")
        invalidInvite("""{"space":"$space","login":"robinjoon","role":"editor"}""")
        mockMvc
            .perform(
                patch("/api/memberships/{id}", waiting.id.value)
                    .principal(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"role":"editor"}"""),
            ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("not_found"))
    }

    private fun invalidInvite(body: String) {
        mockMvc
            .perform(post("/api/memberships").principal(owner).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("invalid"))
    }

    private fun membershipFields(prefix: String): Array<FieldDescriptor> =
        arrayOf(
            fieldWithPath("${prefix}id").description("멤버십 ID"),
            fieldWithPath("${prefix}space").description("스페이스 ID. 이름은 스페이스 목록에서 합친다"),
            fieldWithPath("${prefix}login").description("참여 중이면 그 사람의 지금 GitHub 로그인, 첫 로그인 전이면 초대한 로그인"),
            fieldWithPath("${prefix}avatarUrl").description("GitHub 공개 아바타"),
            fieldWithPath("${prefix}role").description("editor 또는 commenter"),
            fieldWithPath("${prefix}state").description("pending(첫 로그인 전) 또는 active(로그인함, GitHub 사용자 ID에 고정)"),
            fieldWithPath("${prefix}userId").type(JsonFieldType.STRING).optional().description("고정된 사용자 ID. pending이면 null"),
            fieldWithPath("${prefix}invitedAt").description("초대한 시각"),
            fieldWithPath("${prefix}joinedAt").type(JsonFieldType.STRING).optional().description("첫 로그인으로 고정된 시각. pending이면 null"),
        )
}
