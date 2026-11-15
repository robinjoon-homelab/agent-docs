package xyz.robinjoon.agentdocs.webhook.application.service

import xyz.robinjoon.agentdocs.application.model.ActivityActor
import xyz.robinjoon.agentdocs.application.model.ActivityEntry
import xyz.robinjoon.agentdocs.webhook.domain.WebhookEventType
import xyz.robinjoon.agentdocs.webhook.domain.delivery.EventActor
import xyz.robinjoon.agentdocs.webhook.domain.delivery.EventFacts
import xyz.robinjoon.agentdocs.webhook.domain.delivery.EventSubject
import xyz.robinjoon.agentdocs.webhook.domain.delivery.WebhookEvent

// Reads events from the shared activity log by its public fields only: a confirmed revision is its own entry kind, and
// an entry that created a comment carries that comment with its mentions. Summaries are never read, so no text leaves.
internal object WebhookEvents {
    const val REVISION_CONFIRMED = "document.revision_confirmed"

    fun of(
        entry: ActivityEntry,
        publicUrl: String,
    ): List<WebhookEvent> {
        val record = entry.record
        val space = record.subject.spaceId ?: return emptyList()
        val document = record.subject.documentId ?: return emptyList()
        val comment = record.createdComment
        val subject = EventSubject(space, document, record.subject.threadId, comment?.id, record.subject.revision)
        val facts = EventFacts(entry.id, record.at, subject, actorOf(record.actor))
        return when {
            comment != null && subject.threadId != null -> {
                listOf(WebhookEvent.of(WebhookEventType.COMMENT_CREATED, facts, null, publicUrl)) +
                    comment.mentions.sorted().map { WebhookEvent.of(WebhookEventType.COMMENT_MENTIONED, facts, it, publicUrl) }
            }

            record.kind == REVISION_CONFIRMED && subject.revision != null -> {
                listOf(WebhookEvent.of(WebhookEventType.DOCUMENT_CHANGED, facts, null, publicUrl))
            }

            else -> {
                emptyList()
            }
        }
    }

    private fun actorOf(actor: ActivityActor): EventActor = EventActor(actor.kind.name.lowercase(), actor.login, actor.clientName)
}
