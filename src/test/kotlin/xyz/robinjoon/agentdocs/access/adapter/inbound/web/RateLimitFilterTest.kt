package xyz.robinjoon.agentdocs.access.adapter.inbound.web

import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.FilterChain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import tools.jackson.databind.json.JsonMapper
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.agentCaller
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.now
import java.time.Clock

class RateLimitFilterTest {
    @AfterEach
    fun clearAuthentication() = SecurityContextHolder.clearContext()

    @Test
    fun `the 601st request has code fix and Retry-After and the next minute accepts requests`() {
        val clock = mockk<Clock>()
        every { clock.instant() } returns now.plusSeconds(15)
        SecurityContextHolder.getContext().authentication = CallerAuthentication(agentCaller)
        val filter = RateLimitFilter(clock)
        val chain = mockk<FilterChain>(relaxed = true)
        repeat(600) {
            val accepted = MockHttpServletResponse()
            filter.doFilter(MockHttpServletRequest(), accepted, chain)
            assertThat(accepted.status).isEqualTo(200)
        }
        val refused = MockHttpServletResponse()
        filter.doFilter(MockHttpServletRequest(), refused, chain)
        val body = JsonMapper.builder().build().readTree(refused.contentAsString)

        assertThat(refused.status).isEqualTo(429)
        assertThat(refused.getHeader("Retry-After")).isEqualTo("45")
        assertThat(body.get("code").stringValue()).isEqualTo("rate_limited")
        assertThat(body.get("fix").stringValue()).contains("Retry-After")
        every { clock.instant() } returns now.plusSeconds(60)
        val nextMinute = MockHttpServletResponse()
        filter.doFilter(MockHttpServletRequest(), nextMinute, chain)
        assertThat(nextMinute.status).isEqualTo(200)
    }
}
