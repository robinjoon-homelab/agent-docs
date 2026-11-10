package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import xyz.robinjoon.agentdocs.access.adapter.inbound.web.BearerTokenFilter
import xyz.robinjoon.agentdocs.access.adapter.inbound.web.RateLimitFilter
import xyz.robinjoon.agentdocs.access.application.model.Caller
import xyz.robinjoon.agentdocs.access.application.model.CallerAgent
import xyz.robinjoon.agentdocs.access.application.model.CallerScope
import xyz.robinjoon.agentdocs.access.application.model.UploadToken
import xyz.robinjoon.agentdocs.access.application.port.input.CallerUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.UploadGrantUseCase
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class UploadGateFilterTest {
    private val connection = UUID.randomUUID()
    private val forged = UploadToken.of(connection, "made-up")
    private val genuine = UploadToken.of(connection, "secret")
    private val agent =
        Caller(UUID.randomUUID(), "robinjoon", false, CallerAgent(connection, "Claude Code"), setOf(UUID.randomUUID()), CallerScope.EDIT)
    private val uploads = mockk<UploadGrantUseCase>()
    private val callers = mockk<CallerUseCase>()
    private val limiter = RateLimitFilter(Clock.fixed(Instant.parse("2026-10-18T09:00:45Z"), ZoneOffset.UTC), limit = 1)
    private val endpoint =
        object : HttpServlet() {
            override fun service(
                request: HttpServletRequest,
                response: HttpServletResponse,
            ) {
                response.writer.write("reached")
            }
        }

    @AfterEach
    fun clearContext() = SecurityContextHolder.clearContext()

    @Test
    fun `a made-up secret naming a real connection is refused and costs that connection nothing`() {
        every { uploads.admits(connection, "made-up") } returns false
        every { callers.callerForUpload(forged) } returns agent
        every { callers.callerFor("adt_a_real") } returns agent

        val refused = send(post(forged))
        val owner = send(MockHttpServletRequest("POST", "/mcp").apply { addHeader("Authorization", "Bearer adt_a_real") })

        assertThat(refused.status).isEqualTo(401)
        assertThat(refused.contentType).isEqualTo("application/problem+json")
        assertThat(refused.contentAsString).contains("\"code\":\"upload_rejected\"", "docs_upload").doesNotContain("reached", "made-up")
        assertThat(owner.status).isEqualTo(200)
        assertThat(owner.contentAsString).isEqualTo("reached")
    }

    @Test
    fun `a genuine token goes on to the endpoint and counts against its connection`() {
        every { uploads.admits(connection, "secret") } returns true
        every { callers.callerForUpload(genuine) } returns agent

        val first = send(post(genuine))
        val second = send(post(genuine))

        assertThat(first.contentAsString).isEqualTo("reached")
        assertThat(second.status).isEqualTo(429)
    }

    @Test
    fun `requests that are not an upload token on the upload endpoint are left to the usual checks`() {
        val elsewhere = MockHttpServletRequest("GET", "/api/spaces").apply { addHeader("Authorization", "Bearer $forged") }
        val noHeader = MockHttpServletRequest("POST", "/api/uploads")

        listOf(elsewhere, noHeader, post("adt_s_script"), post("adt_u_not-a-token")).forEach { request ->
            val response = MockHttpServletResponse()

            MockFilterChain(endpoint, UploadGateFilter(uploads)).doFilter(request, response)

            assertThat(response.contentAsString).isEqualTo("reached")
        }
    }

    private fun post(token: String): MockHttpServletRequest =
        MockHttpServletRequest("POST", "/api/uploads").apply { addHeader("Authorization", "Bearer $token") }

    private fun send(request: MockHttpServletRequest): MockHttpServletResponse {
        val response = MockHttpServletResponse()
        SecurityContextHolder.clearContext()
        MockFilterChain(endpoint, UploadGateFilter(uploads), BearerTokenFilter(callers), limiter).doFilter(request, response)
        return response
    }
}
