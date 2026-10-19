package xyz.robinjoon.agentdocs.workspace.domain.inbox

import xyz.robinjoon.agentdocs.workspace.domain.Actor

// What an inbox item points at and who caused it. The excerpt is the start of the comment, so the inbox reads without
// opening every document.
@ConsistentCopyVisibility
data class Notice private constructor(
    val kind: InboxKind,
    val target: NoticeTarget,
    val actor: Actor,
    val excerpt: String,
) {
    companion object {
        private const val EXCERPT_LENGTH = 200
        private val whitespace = Regex("\\s+")

        fun of(
            kind: InboxKind,
            target: NoticeTarget,
            actor: Actor,
            body: String,
        ): Notice = Notice(kind, target, actor, body.replace(whitespace, " ").trim().take(EXCERPT_LENGTH))

        fun restore(
            kind: InboxKind,
            target: NoticeTarget,
            actor: Actor,
            excerpt: String,
        ): Notice = Notice(kind, target, actor, excerpt)
    }
}
