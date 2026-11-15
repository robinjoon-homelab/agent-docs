package xyz.robinjoon.agentdocs.webhook.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.webhook.domain.delivery.EventActor
import xyz.robinjoon.agentdocs.webhook.domain.delivery.EventFacts
import xyz.robinjoon.agentdocs.webhook.domain.delivery.EventSubject
import xyz.robinjoon.agentdocs.webhook.domain.delivery.WebhookEvent
import java.time.Instant
import java.util.UUID

class WebhookEventTest {
    private val at = Instant.parse("2026-11-16T03:00:00.123456Z")
    private val space = UUID.fromString("33333333-3333-4333-8333-333333333333")
    private val document = UUID.fromString("66666666-6666-4666-8666-666666666666")
    private val thread = UUID.fromString("77777777-7777-4777-8777-777777777777")
    private val comment = UUID.fromString("88888888-8888-4888-8888-888888888888")

    @Test
    fun `a document change carries ids, the revision, the actor, and a link, and nothing of the content`() {
        val facts = EventFacts(12, at, EventSubject(space, document, null, null, 4), EventActor("human", "robinjoon", null))

        val event = WebhookEvent.of(WebhookEventType.DOCUMENT_CHANGED, facts, null, "https://docs.example.com/")

        assertThat(event.payload).isEqualTo(
            """{"type":"document.changed","timestamp":"2026-11-16T03:00:00.123456Z","data":{"spaceId":"$space",""" +
                """"documentId":"$document","revision":4,"actor":{"kind":"human","login":"robinjoon","client":null},""" +
                """"url":"https://docs.example.com/d/$document"}}""",
        )
        assertThat(event.source).isEqualTo(12)
        assertThat(event.occurredAt).isEqualTo(at)
    }

    @Test
    fun `a mention names the mentioned login and links to the thread, escaping the agent label`() {
        val actor = EventActor("agent", "robinjoon", "Claude \"Code\" (claude.ai)")
        val facts = EventFacts(13, at, EventSubject(space, document, thread, comment, null), actor)

        val event = WebhookEvent.of(WebhookEventType.COMMENT_MENTIONED, facts, "alice", "https://docs.example.com")

        assertThat(event.mentioned).isEqualTo("alice")
        assertThat(event.payload).isEqualTo(
            """{"type":"comment.mentioned","timestamp":"2026-11-16T03:00:00.123456Z","data":{"spaceId":"$space",""" +
                """"documentId":"$document","threadId":"$thread","commentId":"$comment","mentioned":"alice",""" +
                """"actor":{"kind":"agent","login":"robinjoon","client":"Claude \"Code\" (claude.ai)"},""" +
                """"url":"https://docs.example.com/d/$document#thread-$thread"}}""",
        )
    }

    @Test
    fun `an event needs the fact its type is about`() {
        val noRevision = EventFacts(14, at, EventSubject(space, document, null, null, null), EventActor("system", null, null))
        val noThread = EventFacts(15, at, EventSubject(space, document, null, comment, null), EventActor("system", null, null))

        assertThatThrownBy { WebhookEvent.of(WebhookEventType.DOCUMENT_CHANGED, noRevision, null, "https://docs.example.com") }
            .isInstanceOf(InvalidWebhookException::class.java)
        assertThatThrownBy { WebhookEvent.of(WebhookEventType.COMMENT_CREATED, noThread, null, "https://docs.example.com") }
            .isInstanceOf(InvalidWebhookException::class.java)
        assertThatThrownBy { WebhookEvent.of(WebhookEventType.COMMENT_MENTIONED, noThread, null, "https://docs.example.com") }
            .isInstanceOf(InvalidWebhookException::class.java)
    }
}
