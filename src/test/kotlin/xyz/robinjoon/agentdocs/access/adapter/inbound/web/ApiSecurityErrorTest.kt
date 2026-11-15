package xyz.robinjoon.agentdocs.access.adapter.inbound.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.InsufficientAuthenticationException
import org.springframework.security.web.csrf.MissingCsrfTokenException
import tools.jackson.databind.json.JsonMapper
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.settings

class ApiSecurityErrorTest {
    private val json = JsonMapper.builder().build()

    @Test
    fun `unauthenticated requests carry a code fix and resource challenge`() {
        val response = MockHttpServletResponse()
        ApiAuthenticationEntryPoint(settings).commence(
            MockHttpServletRequest(),
            response,
            InsufficientAuthenticationException("missing token"),
        )
        val body = json.readTree(response.contentAsString)

        assertThat(response.status).isEqualTo(401)
        assertThat(response.getHeader("WWW-Authenticate")).contains("/.well-known/oauth-protected-resource/mcp")
        assertThat(body.get("code").stringValue()).isEqualTo("unauthenticated")
        assertThat(body.get("fix").stringValue()).isNotBlank()
    }

    @Test
    fun `CSRF and role failures carry forbidden code and the relevant fix`() {
        val handler = ApiAccessDeniedHandler()
        val csrf = MockHttpServletResponse()
        handler.handle(MockHttpServletRequest(), csrf, MissingCsrfTokenException("missing"))
        val denied = MockHttpServletResponse()
        handler.handle(MockHttpServletRequest(), denied, AccessDeniedException("denied"))

        assertThat(csrf.status).isEqualTo(403)
        assertThat(json.readTree(csrf.contentAsString).get("code").stringValue()).isEqualTo("forbidden")
        assertThat(json.readTree(csrf.contentAsString).get("fix").stringValue()).contains("CSRF")
        assertThat(denied.status).isEqualTo(403)
        assertThat(json.readTree(denied.contentAsString).get("code").stringValue()).isEqualTo("forbidden")
        assertThat(json.readTree(denied.contentAsString).get("fix").stringValue()).contains("permitted")
    }
}
