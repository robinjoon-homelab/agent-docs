package xyz.robinjoon.agentdocs.webhook.adapter.inbound.web

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import xyz.robinjoon.agentdocs.webhook.application.model.EndpointRegistration
import xyz.robinjoon.agentdocs.webhook.application.port.input.WebhookEndpointUseCase
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookEndpointId
import java.net.URI
import java.security.Principal
import java.util.UUID

// The owner's webhook settings. Agents are refused even with an edit connection; no MCP tool manages webhooks.
@RestController
@RequestMapping("/api/webhooks")
class WebhookController(
    private val webhooks: WebhookEndpointUseCase,
) {
    @GetMapping
    fun list(principal: Principal?): List<WebhookEndpointResponse> =
        webhooks.list(WebhookOperators.of(principal)).map(WebhookEndpointResponse::from)

    @PostMapping
    fun register(
        @RequestBody request: RegisterWebhookRequest,
        principal: Principal?,
    ): ResponseEntity<IssuedWebhookResponse> {
        val issued = webhooks.register(WebhookOperators.of(principal), EndpointRegistration(request.url, request.events.toSet()))
        return ResponseEntity.created(URI.create("/api/webhooks/${issued.endpoint.id}")).body(IssuedWebhookResponse.from(issued))
    }

    @PostMapping("/{id}/stop")
    fun stop(
        @PathVariable id: UUID,
        principal: Principal?,
    ): WebhookEndpointResponse = WebhookEndpointResponse.from(webhooks.stop(WebhookOperators.of(principal), WebhookEndpointId(id)))

    @PostMapping("/{id}/secret")
    fun rotateSecret(
        @PathVariable id: UUID,
        principal: Principal?,
    ): IssuedWebhookResponse = IssuedWebhookResponse.from(webhooks.rotateSecret(WebhookOperators.of(principal), WebhookEndpointId(id)))

    @GetMapping("/{id}/deliveries")
    fun deliveries(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "50") limit: Int,
        principal: Principal?,
    ): List<WebhookDeliveryResponse> =
        webhooks.deliveries(WebhookOperators.of(principal), WebhookEndpointId(id), limit).map(WebhookDeliveryResponse::from)
}
