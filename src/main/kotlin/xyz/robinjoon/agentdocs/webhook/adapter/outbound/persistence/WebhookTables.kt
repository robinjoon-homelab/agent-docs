package xyz.robinjoon.agentdocs.webhook.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import xyz.robinjoon.agentdocs.webhook.domain.WebhookEventType
import xyz.robinjoon.agentdocs.webhook.domain.delivery.DeliveryState
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.StopReason

// event_types holds the subscribed event names separated by commas.
internal object WebhookEndpointsTable : Table("webhook_endpoints") {
    val id = javaUUID("id")
    val url = varchar("url", 2000)
    val eventTypes = varchar("event_types", 100)
    val signingSecret = varchar("signing_secret", 100)
    val createdAt = timestampWithTimeZone("created_at")
    val secretRotatedAt = timestampWithTimeZone("secret_rotated_at").nullable()
    val stoppedAt = timestampWithTimeZone("stopped_at").nullable()
    val stopReason = enumerationByName("stop_reason", 10, StopReason::class).nullable()

    override val primaryKey = PrimaryKey(id)
}

// mentioned_login is empty for events about no single mention, so the unique key compares it like any value.
internal object WebhookDeliveriesTable : Table("webhook_deliveries") {
    val id = javaUUID("id")
    val endpointId = javaUUID("endpoint_id")
    val eventType = enumerationByName("event_type", 20, WebhookEventType::class)
    val sourceActivityId = long("source_activity_id")
    val mentionedLogin = varchar("mentioned_login", 39)
    val occurredAt = timestampWithTimeZone("occurred_at")
    val payload = text("payload")
    val state = enumerationByName("delivery_state", 10, DeliveryState::class)
    val attempts = integer("attempts")
    val nextAttemptAt = timestampWithTimeZone("next_attempt_at").nullable()
    val lastAttemptAt = timestampWithTimeZone("last_attempt_at").nullable()
    val lastStatus = integer("last_status").nullable()
    val lastError = varchar("last_error", 40).nullable()

    override val primaryKey = PrimaryKey(id)
}
