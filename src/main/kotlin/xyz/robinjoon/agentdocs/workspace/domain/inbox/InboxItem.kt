package xyz.robinjoon.agentdocs.workspace.domain.inbox

import xyz.robinjoon.agentdocs.domain.AggregateRoot
import java.time.Instant

// One notification for one person, kept with its read state. Agents have no inbox: they look for work by querying
// open threads when a person runs them.
@AggregateRoot
@ConsistentCopyVisibility
data class InboxItem private constructor(
    val id: InboxItemId,
    val recipient: InboxRecipient,
    val notice: Notice,
    val delivery: Delivery,
) {
    fun markRead(at: Instant): InboxItem? = if (delivery.readAt == null) copy(delivery = delivery.copy(readAt = at)) else null

    companion object {
        fun deliver(
            id: InboxItemId,
            recipient: String,
            notice: Notice,
            at: Instant,
        ): InboxItem = InboxItem(id, InboxRecipient(recipient.lowercase(), null), notice, Delivery(at, null))

        fun restore(
            id: InboxItemId,
            recipient: InboxRecipient,
            notice: Notice,
            delivery: Delivery,
        ): InboxItem = InboxItem(id, recipient, notice, delivery)
    }
}
