package xyz.robinjoon.agentdocs.webhook.domain.delivery

import xyz.robinjoon.agentdocs.webhook.domain.WebhookEventType

// The Standard Webhooks body: type, the time the event happened, and data. Written by hand with a fixed field order
// because the bytes are signed and the core has no JSON library; every value is an id, a number, a login, a label, or
// a link, and strings are escaped.
internal object WebhookPayload {
    fun of(
        type: WebhookEventType,
        facts: EventFacts,
        mentioned: String?,
        base: String,
    ): String {
        val body =
            listOf(
                "type" to quote(type.wire),
                "timestamp" to quote(facts.occurredAt.toString()),
                "data" to json(data(type, facts, mentioned, base)),
            )
        return json(body)
    }

    private fun data(
        type: WebhookEventType,
        facts: EventFacts,
        mentioned: String?,
        base: String,
    ): List<Pair<String, String>> {
        val subject = facts.subject
        val where = listOf("spaceId" to quote(subject.spaceId.toString()), "documentId" to quote(subject.documentId.toString()))
        val document = "$base/d/${subject.documentId}"
        val comment = listOf("threadId" to quote(subject.threadId?.toString()), "commentId" to quote(subject.commentId?.toString()))
        val specific =
            when (type) {
                WebhookEventType.DOCUMENT_CHANGED -> listOf("revision" to subject.revision.toString())
                WebhookEventType.COMMENT_CREATED -> comment
                WebhookEventType.COMMENT_MENTIONED -> comment + ("mentioned" to quote(mentioned))
            }
        val url = if (type == WebhookEventType.DOCUMENT_CHANGED) document else "$document#thread-${subject.threadId}"
        return where + specific + listOf("actor" to actor(facts.actor), "url" to quote(url))
    }

    private fun actor(actor: EventActor): String =
        json(listOf("kind" to quote(actor.kind), "login" to quote(actor.login), "client" to quote(actor.client)))

    private fun json(fields: List<Pair<String, String>>): String =
        fields.joinToString(",", "{", "}") { (name, value) -> "${quote(name)}:$value" }

    private fun quote(value: String?): String {
        if (value == null) return "null"
        val escaped = StringBuilder(value.length + 2).append('"')
        value.forEach { escaped.append(escape(it)) }
        return escaped.append('"').toString()
    }

    private fun escape(char: Char): String =
        when {
            char == '"' -> "\\\""
            char == '\\' -> "\\\\"
            char < ' ' -> "\\u%04x".format(char.code)
            else -> char.toString()
        }
}
