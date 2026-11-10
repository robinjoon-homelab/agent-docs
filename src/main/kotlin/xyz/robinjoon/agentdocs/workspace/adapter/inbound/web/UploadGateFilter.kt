package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.web.filter.OncePerRequestFilter
import xyz.robinjoon.agentdocs.access.application.model.UploadToken
import xyz.robinjoon.agentdocs.workspace.application.port.input.UploadGrantUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.UploadRejectedException

// Refuses an upload token with a wrong secret before anything else reads the request. The connection a token names is
// no secret, since responses show it, so without this a made-up token could spend that connection's rate limit and make
// the server read a large file.
class UploadGateFilter(
    private val uploads: UploadGrantUseCase,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean = request.requestURI != UploadToken.ENDPOINT

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val token =
            request
                .getHeader("Authorization")
                ?.takeIf { it.startsWith(BEARER, ignoreCase = true) }
                ?.substring(BEARER.length)
                ?.trim()
        val connection = token?.let(UploadToken::connectionOf)
        val secret = token?.let(UploadToken::secretOf)
        if (connection != null && secret != null && !uploads.admits(connection, secret)) {
            refuse(response)
            return
        }
        chain.doFilter(request, response)
    }

    private fun refuse(response: HttpServletResponse) {
        val status = HttpStatus.UNAUTHORIZED
        response.status = status.value()
        response.contentType = "application/problem+json"
        response.writer.write(
            """{"title":"${status.reasonPhrase}","status":${status.value()},"code":"upload_rejected",""" +
                """"detail":"${UploadRejectedException().message}","fix":"${ProblemFixes.UPLOAD_REJECTED}"}""",
        )
    }

    private companion object {
        const val BEARER = "Bearer "
    }
}
