package xyz.robinjoon.agentdocs.access.adapter.inbound.web

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import xyz.robinjoon.agentdocs.access.application.model.ClientRegistration
import xyz.robinjoon.agentdocs.access.application.port.input.ClientRegistrationUseCase

// Dynamic client registration (RFC 7591) for clients without a metadata document.
@RestController
class RegistrationController(
    private val registrations: ClientRegistrationUseCase,
) {
    @PostMapping("/oauth/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(
        @RequestBody request: RegistrationRequest,
    ): Map<String, Any> {
        val client =
            registrations.register(
                ClientRegistration(request.clientName, request.redirectUris.orEmpty(), request.tokenEndpointAuthMethod, request.grantTypes),
            )
        return mapOf(
            "client_id" to client.clientId,
            "client_id_issued_at" to client.registeredAt.epochSecond,
            "client_name" to client.metadata.name,
            "redirect_uris" to client.metadata.redirectUris,
            "token_endpoint_auth_method" to "none",
            "grant_types" to listOf("authorization_code", "refresh_token"),
            "response_types" to listOf("code"),
        )
    }
}

data class RegistrationRequest(
    @param:JsonProperty("client_name") val clientName: String?,
    @param:JsonProperty("redirect_uris") val redirectUris: List<String>?,
    @param:JsonProperty("token_endpoint_auth_method") val tokenEndpointAuthMethod: String?,
    @param:JsonProperty("grant_types") val grantTypes: List<String>?,
)
