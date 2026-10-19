package xyz.robinjoon.agentdocs.workspace.domain.space

import java.util.Collections

// The settings the owner edits together: what the space is called, what it is for, and what it is linked to.
@ConsistentCopyVisibility
data class SpaceProfile private constructor(
    val name: String,
    val description: String,
    val links: List<ExternalLink>,
) {
    init {
        if (name.isBlank() || name.length > MAX_NAME_LENGTH) {
            throw InvalidSpaceException("name must be 1 to $MAX_NAME_LENGTH characters")
        }
        if (description.length > MAX_DESCRIPTION_LENGTH) {
            throw InvalidSpaceException("description must be at most $MAX_DESCRIPTION_LENGTH characters")
        }
        if (links.size > MAX_LINKS) {
            throw InvalidSpaceException("a space has at most $MAX_LINKS links")
        }
        if (links.toSet().size != links.size) {
            throw InvalidSpaceException("each link may appear only once")
        }
    }

    companion object {
        const val MAX_NAME_LENGTH = 100
        const val MAX_DESCRIPTION_LENGTH = 500
        const val MAX_LINKS = 20

        fun of(
            name: String,
            description: String,
            links: List<ExternalLink>,
        ): SpaceProfile = SpaceProfile(name.trim(), description.trim(), Collections.unmodifiableList(links.toList()))
    }
}
