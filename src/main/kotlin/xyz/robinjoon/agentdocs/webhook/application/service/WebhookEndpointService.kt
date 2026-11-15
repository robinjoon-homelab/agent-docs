package xyz.robinjoon.agentdocs.webhook.application.service

import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.application.model.ActivityActor
import xyz.robinjoon.agentdocs.application.model.ActivityActorKind
import xyz.robinjoon.agentdocs.application.model.ActivityRecord
import xyz.robinjoon.agentdocs.application.model.ActivitySubject
import xyz.robinjoon.agentdocs.application.port.output.ActivityReporter
import xyz.robinjoon.agentdocs.webhook.application.model.EndpointRegistration
import xyz.robinjoon.agentdocs.webhook.application.model.IssuedEndpoint
import xyz.robinjoon.agentdocs.webhook.application.model.WebhookOperator
import xyz.robinjoon.agentdocs.webhook.application.port.input.WebhookEndpointNotFoundException
import xyz.robinjoon.agentdocs.webhook.application.port.input.WebhookEndpointUseCase
import xyz.robinjoon.agentdocs.webhook.application.port.input.WebhookOwnerOnlyException
import xyz.robinjoon.agentdocs.webhook.application.port.output.WebhookDeliveryRepository
import xyz.robinjoon.agentdocs.webhook.application.port.output.WebhookEndpointRepository
import xyz.robinjoon.agentdocs.webhook.application.port.output.WebhookSecretSource
import xyz.robinjoon.agentdocs.webhook.domain.WebhookEventType
import xyz.robinjoon.agentdocs.webhook.domain.delivery.WebhookDelivery
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.StopReason
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.Subscription
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookEndpoint
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookEndpointId
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookUrl
import java.time.Clock
import java.time.Instant
import java.util.UUID

// Endpoint changes are logged outside any space like connection changes. The log names the host only, because some
// receivers put a token in the path.
@Transactional(readOnly = true)
class WebhookEndpointService(
    private val endpoints: WebhookEndpointRepository,
    private val deliveries: WebhookDeliveryRepository,
    private val secrets: WebhookSecretSource,
    private val activity: ActivityReporter,
    private val clock: Clock,
) : WebhookEndpointUseCase {
    override fun list(operator: WebhookOperator): List<WebhookEndpoint> {
        requireOwner(operator, "listing webhooks")
        return endpoints.list()
    }

    @Transactional
    override fun register(
        operator: WebhookOperator,
        registration: EndpointRegistration,
    ): IssuedEndpoint {
        requireOwner(operator, "registering webhooks")
        val subscription = Subscription.of(WebhookUrl.of(registration.url), registration.events.map(WebhookEventType::of).toSet())
        val endpoint = WebhookEndpoint.register(WebhookEndpointId(UUID.randomUUID()), subscription, secrets.generate(), now())
        endpoints.add(endpoint)
        record(operator, "webhook.registered", "webhook 추가: ${subscription.url.host}")
        return IssuedEndpoint(endpoint)
    }

    @Transactional
    override fun stop(
        operator: WebhookOperator,
        id: WebhookEndpointId,
    ): WebhookEndpoint {
        requireOwner(operator, "stopping webhooks")
        val current = endpoints.findByIdForUpdate(id) ?: throw WebhookEndpointNotFoundException(id)
        if (!current.active) return current
        val stopped = current.stop(StopReason.OWNER, now())
        endpoints.update(stopped)
        record(operator, "webhook.stopped", "webhook 중지: ${stopped.subscription.url.host}")
        return stopped
    }

    @Transactional
    override fun rotateSecret(
        operator: WebhookOperator,
        id: WebhookEndpointId,
    ): IssuedEndpoint {
        requireOwner(operator, "replacing webhook secrets")
        val current = endpoints.findByIdForUpdate(id) ?: throw WebhookEndpointNotFoundException(id)
        val rotated = current.rotateSecret(secrets.generate(), now())
        endpoints.update(rotated)
        record(operator, "webhook.secret_rotated", "webhook 서명 비밀 교체: ${rotated.subscription.url.host}")
        return IssuedEndpoint(rotated)
    }

    override fun deliveries(
        operator: WebhookOperator,
        id: WebhookEndpointId,
        limit: Int,
    ): List<WebhookDelivery> {
        requireOwner(operator, "reading webhook deliveries")
        endpoints.findById(id) ?: throw WebhookEndpointNotFoundException(id)
        return deliveries.recent(id, limit.coerceIn(1, MAX_DELIVERIES))
    }

    private fun requireOwner(
        operator: WebhookOperator,
        action: String,
    ) {
        if (!operator.managesWebhooks) throw WebhookOwnerOnlyException(action)
    }

    private fun record(
        operator: WebhookOperator,
        kind: String,
        summary: String,
    ) {
        val actor = ActivityActor(ActivityActorKind.HUMAN, operator.userId, operator.login, null, null)
        activity.record(ActivityRecord(kind, actor, ActivitySubject(null, null, null, null), summary.take(SUMMARY_LENGTH), now()))
    }

    private fun now(): Instant = Instant.now(clock)

    private companion object {
        const val MAX_DELIVERIES = 100
        const val SUMMARY_LENGTH = 300
    }
}
