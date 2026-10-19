package xyz.robinjoon.agentdocs.access.adapter.inbound.web

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.headers.HeaderDocumentation.headerWithName
import org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.queryParameters
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import xyz.robinjoon.agentdocs.access.application.model.AccessSettings
import xyz.robinjoon.agentdocs.access.application.model.ApprovalRequest
import xyz.robinjoon.agentdocs.access.application.model.AuthorizationStart
import xyz.robinjoon.agentdocs.access.application.model.AuthorizationView
import xyz.robinjoon.agentdocs.access.application.model.AuthorizeRequest
import xyz.robinjoon.agentdocs.access.application.model.Caller
import xyz.robinjoon.agentdocs.access.application.model.CallerScope
import xyz.robinjoon.agentdocs.access.application.model.ClientRegistration
import xyz.robinjoon.agentdocs.access.application.model.CodeGrant
import xyz.robinjoon.agentdocs.access.application.model.OAuthException
import xyz.robinjoon.agentdocs.access.application.model.PkceParameters
import xyz.robinjoon.agentdocs.access.application.model.RefreshGrant
import xyz.robinjoon.agentdocs.access.application.model.TargetParameters
import xyz.robinjoon.agentdocs.access.application.model.TokenSet
import xyz.robinjoon.agentdocs.access.application.port.input.AuthorizationUseCase
import xyz.robinjoon.agentdocs.access.application.port.input.ClientRegistrationUseCase
import xyz.robinjoon.agentdocs.access.application.port.input.TokenUseCase
import xyz.robinjoon.agentdocs.access.domain.authorization.AuthorizationAsk
import xyz.robinjoon.agentdocs.access.domain.authorization.AuthorizationDecision
import xyz.robinjoon.agentdocs.access.domain.authorization.AuthorizationRequest
import xyz.robinjoon.agentdocs.access.domain.authorization.AuthorizationRequestId
import xyz.robinjoon.agentdocs.access.domain.authorization.PkceChallenge
import xyz.robinjoon.agentdocs.access.domain.client.ClientKind
import xyz.robinjoon.agentdocs.access.domain.client.ClientMetadata
import xyz.robinjoon.agentdocs.access.domain.client.OAuthClient
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionScope
import xyz.robinjoon.agentdocs.support.web.RestDocsWebTest
import xyz.robinjoon.agentdocs.support.web.documented
import java.time.Instant
import java.util.UUID

// The use cases are strict mocks; the OAuth rules belong to the access domain and service tests. This test checks the
// discovery documents, the authorization redirects, and the OAuth error shape of the token endpoint.
class OAuthApiDocumentationTest : RestDocsWebTest() {
    private val authorization = mockk<AuthorizationUseCase>()
    private val tokens = mockk<TokenUseCase>()
    private val registrations = mockk<ClientRegistrationUseCase>()
    private val settings = AccessSettings("robinjoon", emptySet(), "https://docs.example.com")
    private val metadataMvc = restDocsMockMvc(OAuthMetadataController(settings), AccessExceptionHandler())
    private val authorizeMvc = restDocsMockMvc(AuthorizationController(authorization), AccessExceptionHandler())
    private val tokenMvc = restDocsMockMvc(TokenController(tokens), AccessExceptionHandler())
    private val registrationMvc = restDocsMockMvc(RegistrationController(registrations), AccessExceptionHandler())
    private val owner = CallerAuthentication(Caller(UUID.randomUUID(), "robinjoon", true, null, null, CallerScope.OWNER))
    private val clientId = "https://claude.ai/oauth/claude-code-client-metadata"
    private val requestId = AuthorizationRequestId(UUID.fromString("44444444-4444-4444-8444-444444444444"))
    private val challenge = "MpKhuRql5U_ItQvQquj4AyZQ-hTxEsV8TScgj9SGVsY"
    private val space = UUID.fromString("33333333-3333-4333-8333-333333333333")

    @Test
    fun `the protected resource metadata points MCP clients at this authorization server`() {
        metadataMvc
            .perform(get("/.well-known/oauth-protected-resource/mcp"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resource").value("https://docs.example.com/mcp"))
            .andDo(
                documented(
                    "oauth-protected-resource",
                    responseFields(
                        fieldWithPath("resource").description("토큰을 쓸 MCP 엔드포인트"),
                        fieldWithPath("authorization_servers").description("인가 서버. 이 서버 자신이다"),
                        fieldWithPath("scopes_supported").description("read, comment, edit"),
                        fieldWithPath("bearer_methods_supported").description("header"),
                        fieldWithPath("resource_name").description("Agent Docs"),
                    ),
                ),
            )
    }

    @Test
    fun `the authorization server metadata advertises PKCE, public clients, and metadata document client ids`() {
        metadataMvc
            .perform(get("/.well-known/oauth-authorization-server"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.client_id_metadata_document_supported").value(true))
            .andDo(
                documented(
                    "oauth-authorization-server",
                    responseFields(
                        fieldWithPath("issuer").description("발급자. 리다이렉트의 iss와 같다"),
                        fieldWithPath("authorization_endpoint").description("인가 엔드포인트"),
                        fieldWithPath("token_endpoint").description("토큰 엔드포인트"),
                        fieldWithPath("registration_endpoint").description("동적 클라이언트 등록. 메타데이터 문서가 없는 클라이언트용"),
                        fieldWithPath("response_types_supported").description("code"),
                        fieldWithPath("grant_types_supported").description("authorization_code, refresh_token"),
                        fieldWithPath("code_challenge_methods_supported").description("S256만"),
                        fieldWithPath("token_endpoint_auth_methods_supported").description("none. 공개 클라이언트만 받는다"),
                        fieldWithPath("scopes_supported").description("read, comment, edit"),
                        fieldWithPath("client_id_metadata_document_supported").description("https URL client_id 지원"),
                        fieldWithPath("authorization_response_iss_parameter_supported").description("리다이렉트에 iss를 붙인다 (RFC 9207)"),
                    ),
                ),
            )
    }

    @Test
    fun `a valid authorization request continues on the approval screen`() {
        val request =
            AuthorizeRequest(
                "code",
                clientId,
                "http://localhost:50111/callback",
                "xyz",
                PkceParameters(challenge, "S256"),
                TargetParameters("edit", listOf("https://docs.example.com/mcp")),
            )
        every { authorization.begin(request) } returns AuthorizationStart.Started(requestId)

        authorizeMvc
            .perform(
                get("/oauth/authorize")
                    .param("response_type", "code")
                    .param("client_id", clientId)
                    .param("redirect_uri", "http://localhost:50111/callback")
                    .param("state", "xyz")
                    .param("code_challenge", challenge)
                    .param("code_challenge_method", "S256")
                    .param("scope", "edit")
                    .param("resource", "https://docs.example.com/mcp"),
            ).andExpect(status().isFound)
            .andExpect(header().string("Location", "/oauth/authorize?request=$requestId"))
            .andDo(
                documented(
                    "oauth-authorize",
                    queryParameters(
                        parameterWithName("response_type").description("code"),
                        parameterWithName("client_id").description("https 메타데이터 문서 URL 또는 등록한 client_id"),
                        parameterWithName("redirect_uri").description("등록한 리다이렉트. loopback http는 포트가 달라도 된다"),
                        parameterWithName("state").optional().description("그대로 돌려준다"),
                        parameterWithName("code_challenge").description("PKCE S256 challenge"),
                        parameterWithName("code_challenge_method").description("S256"),
                        parameterWithName("scope").optional().description("원하는 범위. 실제 범위는 소유자가 승인 화면에서 정한다"),
                        parameterWithName("resource").optional().description("이 서버의 MCP 엔드포인트 (RFC 8707)"),
                    ),
                    responseHeaders(headerWithName("Location").description("승인 화면")),
                ),
            )
    }

    @Test
    fun `the approval screen reads the request and approving returns where the browser goes next`() {
        val ask =
            AuthorizationAsk(
                clientId,
                "http://localhost:50111/callback",
                "xyz",
                PkceChallenge(challenge),
                "https://docs.example.com/mcp",
                ConnectionScope.EDIT,
            )
        val pending = AuthorizationRequest.restore(requestId, ask, AuthorizationDecision.Pending, Instant.parse("2026-10-18T09:10:00Z"))
        val client =
            OAuthClient.register(
                clientId,
                ClientKind.METADATA_DOCUMENT,
                ClientMetadata.of("Claude Code", listOf("http://localhost/callback")),
                Instant.parse("2026-10-18T09:00:00Z"),
            )
        val redirect = "http://localhost:50111/callback?code=adt_c_x&state=xyz&iss=https%3A%2F%2Fdocs.example.com"
        every { authorization.describe(owner.principal, requestId) } returns AuthorizationView(pending, client)
        every { authorization.approve(owner.principal, requestId, ApprovalRequest(setOf(space), ConnectionScope.EDIT, 90)) } returns
            redirect

        authorizeMvc
            .perform(get("/api/oauth/requests/{id}", requestId.value).principal(owner))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.client.host").value("claude.ai"))
            .andDo(
                documented(
                    "oauth-request",
                    responseFields(
                        fieldWithPath("id").description("인가 요청 ID"),
                        fieldWithPath("client.id").description("client_id"),
                        fieldWithPath("client.name").description("클라이언트가 밝힌 이름"),
                        fieldWithPath(
                            "client.host",
                        ).type(JsonFieldType.STRING).optional().description("메타데이터 문서 client_id의 호스트. 승인 화면에 크게 보인다"),
                        fieldWithPath("client.kind").description("metadata_document 또는 registered"),
                        fieldWithPath("redirectHost").description("돌아갈 호스트"),
                        fieldWithPath("requestedScope").type(JsonFieldType.STRING).optional().description("클라이언트가 요청한 범위"),
                        fieldWithPath("expiresAt").description("이 요청이 끝나는 시각. 10분 뒤"),
                    ),
                ),
            )
        authorizeMvc
            .perform(
                post("/api/oauth/requests/{id}/approve", requestId.value)
                    .principal(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"spaceIds":["$space"],"scope":"edit","expiresInDays":90}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.redirectTo").value(redirect))
            .andDo(
                documented(
                    "oauth-approve",
                    requestFields(
                        fieldWithPath("spaceIds").description("허용할 스페이스. 나중에 만든 스페이스는 들어가지 않는다"),
                        fieldWithPath("scope").description("read, comment, edit"),
                        fieldWithPath("expiresInDays").description("연결 유지 기간, 1~365일"),
                    ),
                    responseFields(fieldWithPath("redirectTo").description("브라우저가 이동할 클라이언트 주소. code, state, iss가 붙는다")),
                ),
            )
    }

    @Test
    fun `the token endpoint exchanges a code and answers in the OAuth shape`() {
        val grant =
            CodeGrant(
                clientId,
                "adt_c_x",
                "dBjftJeZ4CVP-mB92K1uhbHmnd17tLmcpf1Or0dfHq4",
                "http://localhost:50111/callback",
                listOf("https://docs.example.com/mcp"),
            )
        every { tokens.exchange(grant) } returns TokenSet("adt_a_access", "adt_r_refresh", 3_600, "edit")

        tokenMvc
            .perform(
                post("/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("grant_type", "authorization_code")
                    .param("client_id", clientId)
                    .param("code", "adt_c_x")
                    .param("code_verifier", "dBjftJeZ4CVP-mB92K1uhbHmnd17tLmcpf1Or0dfHq4")
                    .param("redirect_uri", "http://localhost:50111/callback")
                    .param("resource", "https://docs.example.com/mcp"),
            ).andExpect(status().isOk)
            .andExpect(header().string("Cache-Control", "no-store"))
            .andDo(
                documented(
                    "oauth-token",
                    responseFields(
                        fieldWithPath("access_token").description("한 시간짜리 액세스 토큰"),
                        fieldWithPath("token_type").description("Bearer"),
                        fieldWithPath("expires_in").description("초"),
                        fieldWithPath("refresh_token").description("한 번만 쓸 수 있는 리프레시 토큰. 쓸 때마다 새로 받는다"),
                        fieldWithPath("scope").description("승인된 범위"),
                    ),
                ),
            )
    }

    @Test
    fun `a used refresh token is invalid_grant`() {
        every { tokens.refresh(RefreshGrant(clientId, "adt_r_used", emptyList())) } throws
            OAuthException("invalid_grant", "the refresh token is unknown, already used, or its connection ended")

        tokenMvc
            .perform(
                post("/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("grant_type", "refresh_token")
                    .param("client_id", clientId)
                    .param("refresh_token", "adt_r_used"),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("invalid_grant"))
            .andDo(
                documented(
                    "oauth-token-error",
                    responseFields(fieldWithPath("error").description("OAuth 오류 코드"), fieldWithPath("error_description").description("설명")),
                ),
            )
    }

    @Test
    fun `dynamic registration returns a public client`() {
        val registration =
            ClientRegistration("Tool", listOf("http://127.0.0.1/callback"), "none", listOf("authorization_code", "refresh_token"))
        every { registrations.register(registration) } returns
            OAuthClient.register(
                "adc_generated",
                ClientKind.REGISTERED,
                ClientMetadata.of("Tool", listOf("http://127.0.0.1/callback")),
                Instant.parse("2026-10-18T09:00:00Z"),
            )

        registrationMvc
            .perform(
                post("/oauth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"client_name":"Tool","redirect_uris":["http://127.0.0.1/callback"],"token_endpoint_auth_method":"none","grant_types":["authorization_code","refresh_token"]}""",
                    ),
            ).andExpect(status().isCreated)
            .andDo(
                documented(
                    "oauth-register",
                    requestFields(
                        fieldWithPath("client_name").description("이름"),
                        fieldWithPath("redirect_uris").description("https 또는 loopback http"),
                        fieldWithPath("token_endpoint_auth_method").optional().description("none만"),
                        fieldWithPath("grant_types").optional().description("authorization_code, refresh_token"),
                    ),
                    responseFields(
                        fieldWithPath("client_id").description("발급한 client_id"),
                        fieldWithPath("client_id_issued_at").description("발급 시각(초)"),
                        fieldWithPath("client_name").description("이름"),
                        fieldWithPath("redirect_uris").description("리다이렉트"),
                        fieldWithPath("token_endpoint_auth_method").description("none"),
                        fieldWithPath("grant_types").description("authorization_code, refresh_token"),
                        fieldWithPath("response_types").description("code"),
                    ),
                ),
            )
    }
}
