package xyz.robinjoon.agentdocs.access.adapter.inbound.web

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import xyz.robinjoon.agentdocs.access.application.model.AccessSettings
import xyz.robinjoon.agentdocs.access.application.model.Caller
import xyz.robinjoon.agentdocs.access.application.model.CallerAgent
import xyz.robinjoon.agentdocs.access.application.model.CallerScope
import xyz.robinjoon.agentdocs.access.application.model.SessionCaller
import xyz.robinjoon.agentdocs.access.application.port.input.CallerUseCase
import xyz.robinjoon.agentdocs.access.application.port.input.WebSessionUseCase
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class SecurityAdaptersTest {
    private val person = Caller(UUID.randomUUID(), "robinjoon", true, null, null, CallerScope.OWNER)
    private val agent =
        Caller(
            UUID.randomUUID(),
            "robinjoon",
            false,
            CallerAgent(UUID.randomUUID(), "Claude Code"),
            setOf(UUID.randomUUID()),
            CallerScope.READ,
        )

    @AfterEach
    fun clearContext() = SecurityContextHolder.clearContext()

    @Test
    fun `a known bearer token authenticates the request as its caller and an unknown one leaves it anonymous`() {
        val callers = mockk<CallerUseCase>()
        every { callers.callerFor("adt_a_known") } returns agent
        every { callers.callerFor("adt_a_unknown") } returns null
        val filter = BearerTokenFilter(callers)

        filter.doFilter(request("Bearer adt_a_known"), MockHttpServletResponse(), MockFilterChain())
        val known = SecurityContextHolder.getContext().authentication?.principal
        SecurityContextHolder.clearContext()
        filter.doFilter(request("Bearer adt_a_unknown"), MockHttpServletResponse(), MockFilterChain())

        assertThat(known).isEqualTo(agent)
        assertThat(SecurityContextHolder.getContext().authentication).isNull()
    }

    @Test
    fun `only the owner holds the owner role, and an agent never does`() {
        val owner = Caller(UUID.randomUUID(), "robinjoon", true, null, null, CallerScope.OWNER)
        val member = owner.copy(owner = false, scope = CallerScope.READ)

        assertThat(CallerAuthentication(owner).authorities.map { it.authority }).containsExactly("ROLE_PERSON", "ROLE_OWNER")
        assertThat(CallerAuthentication(member).authorities.map { it.authority }).containsExactly("ROLE_PERSON")
        assertThat(CallerAuthentication(agent).authorities.map { it.authority }).containsExactly("ROLE_AGENT")
    }

    @Test
    fun `a connection over its per-minute limit gets 429 with the seconds until the next minute`() {
        val filter = RateLimitFilter(Clock.fixed(Instant.parse("2026-10-18T09:00:45Z"), ZoneOffset.UTC), limit = 2)
        SecurityContextHolder.getContext().authentication = CallerAuthentication(agent)
        val responses =
            List(3) {
                MockHttpServletResponse().also { response ->
                    filter.doFilter(MockHttpServletRequest(), response, MockFilterChain())
                }
            }

        assertThat(responses.map { it.status }).containsExactly(200, 200, 429)
        assertThat(responses.last().getHeader("Retry-After")).isEqualTo("15")
    }

    @Test
    fun `an unauthenticated request learns where the protected resource metadata is and which scopes to ask for`() {
        val response = MockHttpServletResponse()

        ApiAuthenticationEntryPoint(
            AccessSettings("robinjoon", emptySet(), "https://docs.example.com"),
        ).commence(MockHttpServletRequest(), response, BadCredentialsException("x"))

        assertThat(response.status).isEqualTo(401)
        assertThat(
            response.getHeader("WWW-Authenticate"),
        ).isEqualTo(
            "Bearer resource_metadata=\"https://docs.example.com/.well-known/oauth-protected-resource/mcp\", scope=\"read comment edit\"",
        )
    }

    @Test
    fun `a refused account is sent to the login page with the reason`() {
        val refused = MockHttpServletResponse()
        val failed = MockHttpServletResponse()

        SignInFailureHandler().onAuthenticationFailure(
            MockHttpServletRequest(),
            refused,
            OAuth2AuthenticationException(OAuth2Error(GitHubUserService.NOT_ALLOWED)),
        )
        SignInFailureHandler().onAuthenticationFailure(MockHttpServletRequest(), failed, BadCredentialsException("x"))

        assertThat(refused.redirectedUrl).isEqualTo("/login?error=not_allowed")
        assertThat(failed.redirectedUrl).isEqualTo("/login?error=failed")
    }

    @Test
    fun `only a path on this server is kept as the place to return to after sign-in`() {
        val local = MockHttpServletRequest().apply { setParameter("next", "/oauth/authorize?request=1") }
        val foreign = MockHttpServletRequest().apply { setParameter("next", "//evil.example/") }

        NextPath.remember(local)
        NextPath.remember(foreign)

        assertThat(NextPath.take(local)).isEqualTo("/oauth/authorize?request=1")
        assertThat(NextPath.take(local)).isNull()
        assertThat(NextPath.take(foreign)).isNull()
    }

    @Test
    fun `a signed-in browser's cookie authenticates the request, and a renewed sign-in gets its cookie again`() {
        val sessions = mockk<WebSessionUseCase>()
        every { sessions.callerFor("adt_w_fresh") } returns SessionCaller(person, false)
        every { sessions.callerFor("adt_w_renewed") } returns SessionCaller(person, true)
        val filter = SessionCookieFilter(sessions, SessionCookie(secure = true))
        val fresh = MockHttpServletResponse()
        val renewed = MockHttpServletResponse()

        filter.doFilter(withCookie("adt_w_fresh"), fresh, MockFilterChain())
        val principal = SecurityContextHolder.getContext().authentication?.principal
        SecurityContextHolder.clearContext()
        filter.doFilter(withCookie("adt_w_renewed"), renewed, MockFilterChain())

        assertThat(principal).isEqualTo(person)
        assertThat(fresh.getHeader("Set-Cookie")).isNull()
        assertThat(renewed.getHeader("Set-Cookie"))
            .contains("agent_docs_session=adt_w_renewed", "Max-Age=2592000", "HttpOnly", "Secure", "SameSite=Lax", "Path=/")
    }

    @Test
    fun `a cookie for a sign-in that ended leaves the request anonymous and is cleared`() {
        val sessions = mockk<WebSessionUseCase>()
        every { sessions.callerFor("adt_w_ended") } returns null
        val response = MockHttpServletResponse()

        SessionCookieFilter(sessions, SessionCookie(secure = false)).doFilter(withCookie("adt_w_ended"), response, MockFilterChain())

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
        assertThat(response.getHeader("Set-Cookie")).contains("agent_docs_session=", "Max-Age=0").doesNotContain("Secure")
    }

    @Test
    fun `signing in gives the browser a sign-in cookie that lasts 30 days and returns to where sign-in started`() {
        val sessions = mockk<WebSessionUseCase>()
        every { sessions.start(person.userId) } returns "adt_w_new"
        val request = MockHttpServletRequest().apply { setParameter("next", "/s/team") }
        NextPath.remember(request)
        val response = MockHttpServletResponse()
        val signedIn = TestingAuthenticationToken(CallerUser(person, mapOf("login" to "robinjoon")), null)

        SignInSuccessHandler(sessions, SessionCookie(secure = true)).onAuthenticationSuccess(request, response, signedIn)

        assertThat(response.getHeader("Set-Cookie")).contains("agent_docs_session=adt_w_new", "Max-Age=2592000", "HttpOnly")
        assertThat(response.redirectedUrl).isEqualTo("/s/team")
    }

    @Test
    fun `signing out ends the browser's sign-in and clears its cookie`() {
        val sessions = mockk<WebSessionUseCase>()
        every { sessions.end("adt_w_mine") } just runs
        val response = MockHttpServletResponse()

        SignOutHandler(sessions, SessionCookie(secure = true)).logout(withCookie("adt_w_mine"), response, null)

        assertThat(response.getHeader("Set-Cookie")).contains("agent_docs_session=", "Max-Age=0")
    }

    private fun withCookie(token: String): MockHttpServletRequest =
        MockHttpServletRequest().apply {
            setCookies(Cookie("agent_docs_session", token))
        }

    private fun request(authorization: String): MockHttpServletRequest =
        MockHttpServletRequest().apply {
            addHeader("Authorization", authorization)
        }
}
