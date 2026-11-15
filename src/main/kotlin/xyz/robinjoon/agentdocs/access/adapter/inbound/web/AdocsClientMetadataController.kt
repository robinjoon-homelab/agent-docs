package xyz.robinjoon.agentdocs.access.adapter.inbound.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import xyz.robinjoon.agentdocs.access.application.model.AccessSettings

@RestController
class AdocsClientMetadataController(
    private val settings: AccessSettings,
) {
    @GetMapping("/oauth/clients/adocs")
    fun metadata(): Map<String, Any> =
        mapOf(
            "client_id" to settings.issuer.trimEnd('/') + "/oauth/clients/adocs",
            "client_name" to "adocs",
            "redirect_uris" to listOf("http://127.0.0.1/callback"),
            "token_endpoint_auth_method" to "none",
            "grant_types" to listOf("authorization_code", "refresh_token"),
            "response_types" to listOf("code"),
        )
}
