package xyz.robinjoon.agentdocs.access.adapter.inbound.web

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import xyz.robinjoon.agentdocs.access.application.model.ApprovalRequest
import xyz.robinjoon.agentdocs.access.application.model.AuthorizationStart
import xyz.robinjoon.agentdocs.access.application.model.AuthorizeRequest
import xyz.robinjoon.agentdocs.access.application.model.PkceParameters
import xyz.robinjoon.agentdocs.access.application.model.TargetParameters
import xyz.robinjoon.agentdocs.access.application.port.input.AuthorizationUseCase
import xyz.robinjoon.agentdocs.access.domain.InvalidAccessException
import xyz.robinjoon.agentdocs.access.domain.authorization.AuthorizationRequestId
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionScope
import java.net.URI
import java.security.Principal
import java.time.Instant
import java.util.UUID

// The browser side of the authorization flow. The client opens /oauth/authorize; a valid request continues on the
// approval screen at the same path with only ?request=, which the web app serves.
@RestController
class AuthorizationController(
    private val authorization: AuthorizationUseCase,
) {
    @GetMapping("/oauth/authorize", params = ["client_id"])
    fun authorize(
        @RequestParam params: Map<String, String>,
        @RequestParam(name = "resource", required = false) resources: List<String>?,
    ): ResponseEntity<String> {
        val request =
            AuthorizeRequest(
                responseType = params["response_type"],
                clientId = params["client_id"],
                redirectUri = params["redirect_uri"],
                state = params["state"],
                pkce = PkceParameters(params["code_challenge"], params["code_challenge_method"]),
                target = TargetParameters(params["scope"], resources.orEmpty()),
            )
        return when (val start = authorization.begin(request)) {
            is AuthorizationStart.Started -> {
                redirect("/oauth/authorize?request=${start.request}")
            }

            is AuthorizationStart.RedirectWithError -> {
                redirect(start.location)
            }

            is AuthorizationStart.Rejected -> {
                ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.TEXT_PLAIN).body("연결을 시작할 수 없습니다: ${start.description}")
            }
        }
    }

    @GetMapping("/api/oauth/requests/{id}")
    fun describe(
        @PathVariable id: UUID,
        principal: Principal?,
    ): AuthorizationRequestResponse {
        val view = authorization.describe(Callers.of(principal), AuthorizationRequestId(id))
        val ask = view.request.ask
        return AuthorizationRequestResponse(
            id = view.request.id.value,
            client =
                ClientResponse(
                    view.client.clientId,
                    view.client.metadata.name,
                    view.client.host,
                    view.client.kind.name
                        .lowercase(),
                ),
            redirectHost = URI(ask.redirectUri).host,
            requestedScope = ask.requestedScope?.wire,
            expiresAt = view.request.expiresAt,
        )
    }

    @PostMapping("/api/oauth/requests/{id}/approve")
    fun approve(
        @PathVariable id: UUID,
        @RequestBody request: ApproveRequest,
        principal: Principal?,
    ): RedirectResponse {
        val scope = ConnectionScope.of(request.scope) ?: throw InvalidAccessException("scope must be read, comment, or edit")
        val approval = ApprovalRequest(request.spaceIds.toSet(), scope, request.expiresInDays)
        return RedirectResponse(authorization.approve(Callers.of(principal), AuthorizationRequestId(id), approval))
    }

    @PostMapping("/api/oauth/requests/{id}/deny")
    fun deny(
        @PathVariable id: UUID,
        principal: Principal?,
    ): RedirectResponse = RedirectResponse(authorization.deny(Callers.of(principal), AuthorizationRequestId(id)))

    private fun redirect(location: String): ResponseEntity<String> =
        ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build()
}

data class AuthorizationRequestResponse(
    val id: UUID,
    val client: ClientResponse,
    val redirectHost: String?,
    val requestedScope: String?,
    val expiresAt: Instant,
)

data class ClientResponse(
    val id: String,
    val name: String,
    val host: String?,
    val kind: String,
)

data class ApproveRequest(
    val spaceIds: List<UUID>,
    val scope: String,
    val expiresInDays: Int,
)

data class RedirectResponse(
    val redirectTo: String,
)
