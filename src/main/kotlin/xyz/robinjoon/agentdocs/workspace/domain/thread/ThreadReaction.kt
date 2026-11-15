package xyz.robinjoon.agentdocs.workspace.domain.thread

import xyz.robinjoon.agentdocs.workspace.domain.Actor

data class ThreadReaction(
    val actor: Actor,
    val emoji: String,
) {
    init {
        if (emoji.isBlank() || emoji.length > 32 || emoji.any { it.isISOControl() || it.isWhitespace() }) {
            throw InvalidThreadException("emoji must contain 1 to 32 non-whitespace characters")
        }
    }
}
