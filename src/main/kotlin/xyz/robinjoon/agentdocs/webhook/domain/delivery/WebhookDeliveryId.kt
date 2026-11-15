package xyz.robinjoon.agentdocs.webhook.domain.delivery

import java.util.UUID

@JvmInline
value class WebhookDeliveryId(
    val value: UUID,
) {
    // The webhook-id header. It names the delivery, so it stays the same on every retry and receivers can drop a
    // repeat; it holds no dot, which the signed content uses as its separator.
    val messageId: String get() = "msg_" + value.toString().replace("-", "")

    override fun toString(): String = value.toString()
}
