package xyz.robinjoon.agentdocs.webhook.application

import xyz.robinjoon.agentdocs.application.model.ActivityActor
import xyz.robinjoon.agentdocs.application.model.ActivityActorKind
import xyz.robinjoon.agentdocs.application.model.ActivityEntry
import xyz.robinjoon.agentdocs.application.model.ActivityRecord
import xyz.robinjoon.agentdocs.application.model.ActivitySubject
import xyz.robinjoon.agentdocs.application.model.CreatedComment
import xyz.robinjoon.agentdocs.webhook.domain.WebhookEventType
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.Subscription
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookEndpoint
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookEndpointId
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookSecret
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookUrl
import java.time.Instant
import java.util.UUID

internal object WebhookFixtures {
    val start: Instant = Instant.parse("2026-11-16T03:00:00Z")
    val space: UUID = UUID.fromString("33333333-3333-4333-8333-333333333333")
    val document: UUID = UUID.fromString("66666666-6666-4666-8666-666666666666")
    val thread: UUID = UUID.fromString("77777777-7777-4777-8777-777777777777")
    val secret: WebhookSecret = WebhookSecret.of(ByteArray(32) { it.toByte() })
    private val person = ActivityActor(ActivityActorKind.HUMAN, UUID.randomUUID(), "robinjoon", null, null)

    fun endpoint(
        events: Set<WebhookEventType> = WebhookEventType.entries.toSet(),
        at: Instant = start,
    ): WebhookEndpoint =
        WebhookEndpoint.register(
            WebhookEndpointId(UUID.randomUUID()),
            Subscription.of(WebhookUrl.of("https://hooks.example.com/agent-docs"), events),
            secret,
            at,
        )

    fun confirmed(
        id: Long,
        at: Instant,
        revision: Int = 2,
    ): ActivityEntry =
        ActivityEntry(
            id,
            ActivityRecord("document.revision_confirmed", person, ActivitySubject(space, document, null, revision), "본문 요약", at),
        )

    fun commented(
        id: Long,
        at: Instant,
        mentions: Set<String> = emptySet(),
    ): ActivityEntry =
        ActivityEntry(
            id,
            ActivityRecord(
                "comment.added",
                person,
                ActivitySubject(space, document, thread, null),
                "제목: 댓글 본문",
                at,
                CreatedComment(UUID.randomUUID(), mentions),
            ),
        )

    // Kinds the webhook never reads, even with a revision: an autosave and a plain revised entry.
    fun ignored(
        id: Long,
        at: Instant,
    ): ActivityEntry = ActivityEntry(id, ActivityRecord("document.revised", person, ActivitySubject(space, document, null, 2), "요약", at))
}
