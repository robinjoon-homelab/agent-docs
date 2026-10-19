package xyz.robinjoon.agentdocs.access.adapter.inbound.web

import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import xyz.robinjoon.agentdocs.access.application.model.CodeGrant
import xyz.robinjoon.agentdocs.access.application.model.OAuthException
import xyz.robinjoon.agentdocs.access.application.model.RefreshGrant
import xyz.robinjoon.agentdocs.access.application.model.TokenSet
import xyz.robinjoon.agentdocs.access.application.port.input.TokenUseCase

// The token endpoint takes form parameters and answers in the OAuth JSON shape, never cached (RFC 6749 section 5.1).
@RestController
class TokenController(
    private val tokens: TokenUseCase,
) {
    @PostMapping("/oauth/token", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    fun token(
        @RequestParam params: Map<String, String>,
        @RequestParam(name = "resource", required = false) resources: List<String>?,
    ): ResponseEntity<Map<String, Any>> {
        val issued =
            when (params["grant_type"]) {
                "authorization_code" -> {
                    tokens.exchange(
                        CodeGrant(
                            params["client_id"],
                            params["code"],
                            params["code_verifier"],
                            params["redirect_uri"],
                            resources.orEmpty(),
                        ),
                    )
                }

                "refresh_token" -> {
                    tokens.refresh(RefreshGrant(params["client_id"], params["refresh_token"], resources.orEmpty()))
                }

                else -> {
                    throw OAuthException("unsupported_grant_type", "grant_type must be authorization_code or refresh_token")
                }
            }
        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.noStore())
            .header("Pragma", "no-cache")
            .body(bodyOf(issued))
    }

    private fun bodyOf(tokens: TokenSet): Map<String, Any> =
        mapOf(
            "access_token" to tokens.accessToken,
            "token_type" to "Bearer",
            "expires_in" to tokens.expiresIn,
            "refresh_token" to tokens.refreshToken,
            "scope" to tokens.scope,
        )
}
