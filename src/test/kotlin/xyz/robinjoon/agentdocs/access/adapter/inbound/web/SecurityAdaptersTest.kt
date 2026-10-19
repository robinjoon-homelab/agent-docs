package xyz.robinjoon.agentdocs.access.adapter.inbound.web

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import xyz.robinjoon.agentdocs.access.application.model.AccessSettings
import xyz.robinjoon.agentdocs.access.application.model.Caller
import xyz.robinjoon.agentdocs.access.application.model.CallerAgent
import xyz.robinjoon.agentdocs.access.application.model.CallerScope
import xyz.robinjoon.agentdocs.access.application.port.input.CallerUseCase
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class SecurityAdaptersTest {
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
    fun `an unauthenticated request learns where the protected resource metadata is`() {
        val response = MockHttpServletResponse()

        ApiAuthenticationEntryPoint(
            AccessSettings("robinjoon", emptySet(), "https://docs.example.com"),
        ).commence(MockHttpServletRequest(), response, BadCredentialsException("x"))

        assertThat(response.status).isEqualTo(401)
        assertThat(
            response.getHeader("WWW-Authenticate"),
        ).isEqualTo("Bearer resource_metadata=\"https://docs.example.com/.well-known/oauth-protected-resource/mcp\"")
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

    private fun request(authorization: String): MockHttpServletRequest =
        MockHttpServletRequest().apply {
            addHeader("Authorization", authorization)
        }
}
