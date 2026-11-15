package xyz.robinjoon.agentdocs.webhook.domain.delivery

import xyz.robinjoon.agentdocs.webhook.domain.InvalidWebhookException
import xyz.robinjoon.agentdocs.webhook.domain.WebhookEventType
import java.time.Instant
import java.util.UUID

// One event as it is sent, with its body fixed when it is planned, so every attempt sends the same bytes. source is
// the activity log entry it came from and mentioned the login of a mention event. The body carries only ids, the actor,
// the time, and a link: no title, summary, comment text, or token leaves the server.
@ConsistentCopyVisibility
data class WebhookEvent private constructor(
    val type: WebhookEventType,
    val source: Long,
    val mentioned: String?,
    val occurredAt: Instant,
    val payload: String,
) {
    companion object {
        fun of(
            type: WebhookEventType,
            facts: EventFacts,
            mentioned: String?,
            publicUrl: String,
        ): WebhookEvent {
            requireFacts(type, facts.subject, mentioned)
            val payload = WebhookPayload.of(type, facts, mentioned, publicUrl.trimEnd('/'))
            return WebhookEvent(type, facts.source, mentioned, facts.occurredAt, payload)
        }

        fun restore(
            type: WebhookEventType,
            facts: StoredEvent,
            mentioned: String?,
        ): WebhookEvent = WebhookEvent(type, facts.source, mentioned, facts.occurredAt, facts.payload)

        private fun requireFacts(
            type: WebhookEventType,
            subject: EventSubject,
            mentioned: String?,
        ) {
            val complete =
                when (type) {
                    WebhookEventType.DOCUMENT_CHANGED -> subject.revision != null && mentioned == null
                    WebhookEventType.COMMENT_CREATED -> subject.threadId != null && mentioned == null
                    WebhookEventType.COMMENT_MENTIONED -> subject.threadId != null && !mentioned.isNullOrEmpty()
                }
            if (!complete) throw InvalidWebhookException("${type.wire} needs its revision, thread, or mentioned login")
        }
    }
}

// What a planned event was stored with.
data class StoredEvent(
    val source: Long,
    val occurredAt: Instant,
    val payload: String,
)

// The facts of the activity an event comes from.
data class EventFacts(
    val source: Long,
    val occurredAt: Instant,
    val subject: EventSubject,
    val actor: EventActor,
)

data class EventSubject(
    val spaceId: UUID,
    val documentId: UUID,
    val threadId: UUID?,
    val commentId: UUID?,
    val revision: Int?,
)

// kind is human, agent, or system; client is the agent's label with its client host.
data class EventActor(
    val kind: String,
    val login: String?,
    val client: String?,
)
