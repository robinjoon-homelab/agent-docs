package xyz.robinjoon.agentdocs.application.model

import java.util.UUID

// The comment an activity actually created, with the lowercase logins it mentioned under the inbox rule. Only an entry
// that created a comment carries one, so readers can tell creation apart from edits without parsing kind names.
data class CreatedComment(
    val id: UUID,
    val mentions: Set<String>,
) {
    init {
        require(mentions.all { it.isNotEmpty() && it.length <= MAX_LOGIN && it == it.lowercase() }) {
            "mentions are lowercase GitHub logins"
        }
    }

    private companion object {
        // GitHub logins are at most 39 characters, the width of every login column.
        const val MAX_LOGIN = 39
    }
}
