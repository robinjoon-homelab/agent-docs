package xyz.robinjoon.agentdocs.workspace.domain.space

import java.util.Collections

// The settings the owner edits together: what the space is called, what it is for, what it is linked to, and what it
// points agents to. The guidance lives here because its default repository must be one of these links: whenever the
// links change, a default that is no longer linked is dropped.
@ConsistentCopyVisibility
data class SpaceProfile private constructor(
    val name: String,
    val description: String,
    val links: List<ExternalLink>,
    val guidance: SpaceGuidance,
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

    fun guidedBy(guidance: SpaceGuidance): SpaceProfile = copy(guidance = guidance.within(links))

    companion object {
        const val MAX_NAME_LENGTH = 100
        const val MAX_DESCRIPTION_LENGTH = 500
        const val MAX_LINKS = 20

        fun of(
            name: String,
            description: String,
            links: List<ExternalLink>,
            guidance: SpaceGuidance = SpaceGuidance.none,
        ): SpaceProfile {
            val kept = Collections.unmodifiableList(links.toList())
            return SpaceProfile(name.trim(), description.trim(), kept, guidance.within(kept))
        }
    }
}
