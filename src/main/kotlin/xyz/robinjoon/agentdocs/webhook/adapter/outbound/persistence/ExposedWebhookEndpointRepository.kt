package xyz.robinjoon.agentdocs.webhook.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import xyz.robinjoon.agentdocs.webhook.application.port.output.WebhookEndpointRepository
import xyz.robinjoon.agentdocs.webhook.domain.WebhookEventType
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.EndpointStop
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.EndpointTimeline
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.Subscription
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookEndpoint
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookEndpointId
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookSecret
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookUrl
import java.time.ZoneOffset

class ExposedWebhookEndpointRepository : WebhookEndpointRepository {
    override fun add(endpoint: WebhookEndpoint) {
        WebhookEndpointsTable.insert {
            it[id] = endpoint.id.value
            write(it, endpoint)
        }
    }

    override fun update(endpoint: WebhookEndpoint) {
        val updated = WebhookEndpointsTable.update({ WebhookEndpointsTable.id eq endpoint.id.value }) { write(it, endpoint) }
        check(updated == 1) { "Webhook endpoint to update does not exist: ${endpoint.id}" }
    }

    override fun findById(id: WebhookEndpointId): WebhookEndpoint? = byId(id).singleOrNull()?.toEndpoint()

    override fun findByIdForUpdate(id: WebhookEndpointId): WebhookEndpoint? = byId(id).forUpdate().singleOrNull()?.toEndpoint()

    override fun list(): List<WebhookEndpoint> =
        WebhookEndpointsTable
            .selectAll()
            .orderBy(WebhookEndpointsTable.createdAt to SortOrder.DESC, WebhookEndpointsTable.id to SortOrder.ASC)
            .map { it.toEndpoint() }

    // Locked in id order, so passes that lock the same rows never wait on each other in a cycle.
    override fun lockActive(): List<WebhookEndpoint> =
        WebhookEndpointsTable
            .selectAll()
            .where { WebhookEndpointsTable.stoppedAt.isNull() }
            .orderBy(WebhookEndpointsTable.id to SortOrder.ASC)
            .forUpdate()
            .map { it.toEndpoint() }

    private fun byId(id: WebhookEndpointId) = WebhookEndpointsTable.selectAll().where { WebhookEndpointsTable.id eq id.value }

    private fun write(
        row: UpdateBuilder<*>,
        endpoint: WebhookEndpoint,
    ) {
        val timeline = endpoint.timeline
        row[WebhookEndpointsTable.url] = endpoint.subscription.url.toString()
        row[WebhookEndpointsTable.eventTypes] =
            endpoint.subscription.events
                .map { it.name }
                .sorted()
                .joinToString(",")
        row[WebhookEndpointsTable.signingSecret] = endpoint.secret.serialized
        row[WebhookEndpointsTable.createdAt] = timeline.createdAt.atOffset(ZoneOffset.UTC)
        row[WebhookEndpointsTable.secretRotatedAt] = timeline.secretRotatedAt?.atOffset(ZoneOffset.UTC)
        row[WebhookEndpointsTable.stoppedAt] = timeline.stop?.at?.atOffset(ZoneOffset.UTC)
        row[WebhookEndpointsTable.stopReason] = timeline.stop?.reason
    }

    private fun ResultRow.toEndpoint(): WebhookEndpoint {
        val events = this[WebhookEndpointsTable.eventTypes].split(',').map(WebhookEventType::valueOf).toSet()
        val stoppedAt = this[WebhookEndpointsTable.stoppedAt]?.toInstant()
        val reason = this[WebhookEndpointsTable.stopReason]
        val stop = if (stoppedAt != null && reason != null) EndpointStop(stoppedAt, reason) else null
        val timeline =
            EndpointTimeline(
                this[WebhookEndpointsTable.createdAt].toInstant(),
                this[WebhookEndpointsTable.secretRotatedAt]?.toInstant(),
                stop,
            )
        return WebhookEndpoint.restore(
            WebhookEndpointId(this[WebhookEndpointsTable.id]),
            Subscription.of(WebhookUrl.restore(this[WebhookEndpointsTable.url]), events),
            WebhookSecret.parse(this[WebhookEndpointsTable.signingSecret]),
            timeline,
        )
    }
}
