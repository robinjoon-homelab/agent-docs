package xyz.robinjoon.agentdocs.access.adapter.inbound.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import xyz.robinjoon.agentdocs.access.application.model.Caller
import java.time.Clock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// At most 600 requests per connection per clock minute. The count lives in memory, which is enough for the one
// application instance the homelab runs.
class RateLimitFilter(
    private val clock: Clock,
    private val limit: Int = DEFAULT_LIMIT,
) : OncePerRequestFilter() {
    private val windows = ConcurrentHashMap<UUID, Window>()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val connection =
            (
                SecurityContextHolder
                    .getContextHolderStrategy()
                    .context.authentication
                    ?.principal as? Caller
            )?.agent?.connectionId
        val epochSecond = clock.instant().epochSecond
        if (connection != null && count(connection, epochSecond / SECONDS_PER_MINUTE) > limit) {
            refuse(response, SECONDS_PER_MINUTE - epochSecond % SECONDS_PER_MINUTE)
            return
        }
        chain.doFilter(request, response)
    }

    private fun count(
        connection: UUID,
        minute: Long,
    ): Int =
        requireNotNull(
            windows.compute(connection) { _, old ->
                if (old == null ||
                    old.minute != minute
                ) {
                    Window(minute, 1)
                } else {
                    old.plusOne()
                }
            },
        ).count

    private fun refuse(
        response: HttpServletResponse,
        retryAfter: Long,
    ) {
        response.status = TOO_MANY_REQUESTS
        response.setHeader("Retry-After", retryAfter.toString())
        response.contentType = "application/problem+json"
        response.writer.write(
            """{"title":"Too Many Requests","status":429,"code":"rate_limited","detail":"at most $limit requests a minute per connection"}""",
        )
    }

    private data class Window(
        val minute: Long,
        val count: Int,
    ) {
        fun plusOne(): Window = copy(count = count + 1)
    }

    private companion object {
        const val DEFAULT_LIMIT = 600
        const val SECONDS_PER_MINUTE = 60L
        const val TOO_MANY_REQUESTS = 429
    }
}
