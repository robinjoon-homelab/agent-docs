package xyz.robinjoon.agentdocs.access.adapter.inbound.web

import io.mockk.every
import io.mockk.mockk
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import xyz.robinjoon.agentdocs.access.application.model.Caller
import xyz.robinjoon.agentdocs.access.application.model.CallerAgent
import xyz.robinjoon.agentdocs.access.application.model.CallerScope
import xyz.robinjoon.agentdocs.access.application.model.GrantChange
import xyz.robinjoon.agentdocs.access.application.model.IssuedToken
import xyz.robinjoon.agentdocs.access.application.model.ScriptTokenRequest
import xyz.robinjoon.agentdocs.access.application.port.input.ConnectionUseCase
import xyz.robinjoon.agentdocs.access.application.port.input.OwnerOnlyException
import xyz.robinjoon.agentdocs.access.domain.TokenHash
import xyz.robinjoon.agentdocs.access.domain.connection.ClientRef
import xyz.robinjoon.agentdocs.access.domain.connection.Connection
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionId
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionKind
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionOwner
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionScope
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionState
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionTerm
import xyz.robinjoon.agentdocs.access.domain.connection.Credentials
import xyz.robinjoon.agentdocs.access.domain.connection.Grant
import xyz.robinjoon.agentdocs.access.domain.user.UserId
import xyz.robinjoon.agentdocs.support.web.RestDocsWebTest
import xyz.robinjoon.agentdocs.support.web.documented
import java.time.Duration
import java.time.Instant
import java.util.UUID

class ConnectionApiDocumentationTest : RestDocsWebTest() {
    private val connections = mockk<ConnectionUseCase>()
    private val mockMvc = restDocsMockMvc(ConnectionController(connections), AccessExceptionHandler())
    private val meMvc = restDocsMockMvc(MeController(), AccessExceptionHandler())
    private val userId = UUID.fromString("11111111-1111-4111-8111-111111111111")
    private val ownerCaller = Caller(userId, "robinjoon", true, null, null, CallerScope.OWNER)
    private val owner = CallerAuthentication(ownerCaller)
    private val agent =
        CallerAuthentication(
            Caller(userId, "robinjoon", false, CallerAgent(UUID.randomUUID(), "Claude Code"), emptySet(), CallerScope.READ),
        )
    private val space = UUID.fromString("33333333-3333-4333-8333-333333333333")
    private val at = Instant.parse("2026-10-18T09:00:00Z")
    private val connection =
        connection(ConnectionKind.AGENT, ClientRef("https://claude.ai/oauth/claude-code-client-metadata", "Claude Code", "claude.ai"))

    @Test
    fun `me tells the web app who is signed in`() {
        meMvc
            .perform(get("/api/me").principal(owner))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.avatarUrl").value("https://github.com/robinjoon.png"))
            .andDo(
                documented(
                    "me",
                    responseFields(
                        fieldWithPath("userId").description("사용자 ID"),
                        fieldWithPath("login").description("GitHub 로그인"),
                        fieldWithPath("owner").description("소유자인지. 아니면 문서를 읽기 전용으로 연다"),
                        fieldWithPath("avatarUrl").description("GitHub 공개 아바타"),
                        fieldWithPath("agent").type(JsonFieldType.STRING).optional().description("에이전트 토큰으로 부른 경우 클라이언트 이름"),
                    ),
                ),
            )
        meMvc.perform(get("/api/me")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `the owner lists connections with their granted spaces and scope`() {
        every { connections.list(ownerCaller) } returns listOf(connection)

        mockMvc
            .perform(get("/api/connections").principal(owner))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].clientHost").value("claude.ai"))
            .andDo(documented("connections-list", responseFields(*connectionFields("[]."))))
    }

    @Test
    fun `a script token is shown only in the response that issues it`() {
        val script = connection(ConnectionKind.SCRIPT, ClientRef(null, "backup script", null))
        every { connections.issue(ownerCaller, ScriptTokenRequest("backup script", setOf(space), ConnectionScope.READ, 30)) } returns
            IssuedToken(script, "adt_s_secret")

        mockMvc
            .perform(
                post("/api/connections")
                    .principal(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"backup script","spaceIds":["$space"],"scope":"read","expiresInDays":30}"""),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.token").value("adt_s_secret"))
            .andDo(
                documented(
                    "connections-issue",
                    requestFields(
                        fieldWithPath("name").description("이름"),
                        fieldWithPath("spaceIds").description("허용할 스페이스"),
                        fieldWithPath("scope").description("read, comment, edit"),
                        fieldWithPath("expiresInDays").description("1~365일"),
                    ),
                    responseFields(*connectionFields("connection."), fieldWithPath("token").description("토큰 원문. 다시 보여주지 않는다")),
                ),
            )
    }

    @Test
    fun `narrowing and revoking apply from the connection's next request`() {
        every { connections.regrant(ownerCaller, connection.id, GrantChange(emptySet(), null)) } returns
            connection.regrant(Grant.of(emptySet(), ConnectionScope.EDIT))
        every { connections.revoke(ownerCaller, connection.id) } returns requireNotNull(connection.revoke(at))

        mockMvc
            .perform(
                patch(
                    "/api/connections/{id}",
                    connection.id.value,
                ).principal(owner).contentType(MediaType.APPLICATION_JSON).content("""{"spaceIds":[]}"""),
            ).andExpect(status().isOk)
            .andDo(
                documented(
                    "connections-regrant",
                    requestFields(
                        fieldWithPath("spaceIds").optional().description("새 허용 스페이스"),
                        fieldWithPath("scope").optional().type(JsonFieldType.STRING).description("새 범위"),
                    ),
                    responseFields(*connectionFields("")),
                ),
            )
        mockMvc
            .perform(
                delete("/api/connections/{id}", connection.id.value).principal(owner),
            ).andExpect(status().isNoContent)
            .andDo(documented("connections-revoke"))
    }

    @Test
    fun `an agent cannot manage connections`() {
        every { connections.list(agent.principal) } throws OwnerOnlyException("listing connections")

        mockMvc
            .perform(get("/api/connections").principal(agent))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("forbidden"))
    }

    private fun connection(
        kind: ConnectionKind,
        client: ClientRef,
    ): Connection {
        val term = ConnectionTerm(at, at.plus(Duration.ofDays(90)), null, at)
        val credentials = Credentials(TokenHash.of("access"), at.plus(Duration.ofHours(1)), TokenHash.of("refresh"))
        return Connection.restore(
            ConnectionId(UUID.fromString("22222222-2222-4222-8222-222222222222")),
            ConnectionOwner(UserId(userId), kind, client),
            Grant.of(setOf(space), ConnectionScope.EDIT),
            ConnectionState(term, credentials),
        )
    }

    private fun connectionFields(prefix: String): Array<FieldDescriptor> =
        arrayOf(
            fieldWithPath("${prefix}id").description("연결 ID"),
            fieldWithPath("${prefix}kind").description("agent(MCP OAuth) 또는 script(직접 발급)"),
            fieldWithPath("${prefix}name").description("클라이언트 이름 또는 스크립트 이름"),
            fieldWithPath("${prefix}clientId").type(JsonFieldType.STRING).optional().description("OAuth client_id"),
            fieldWithPath("${prefix}clientHost").type(JsonFieldType.STRING).optional().description("client_id의 호스트"),
            fieldWithPath("${prefix}spaceIds").description("허용 스페이스 ID. 이름은 /api/spaces와 합쳐 보여준다"),
            fieldWithPath("${prefix}scope").description("read, comment, edit"),
            fieldWithPath("${prefix}createdAt").description("만든 시각"),
            fieldWithPath("${prefix}lastUsedAt").type(JsonFieldType.STRING).optional().description("마지막 사용 시각. 1분 단위"),
            fieldWithPath("${prefix}expiresAt").description("만료 시각"),
            fieldWithPath("${prefix}revokedAt").type(JsonFieldType.STRING).optional().description("회수한 시각"),
        )
}
