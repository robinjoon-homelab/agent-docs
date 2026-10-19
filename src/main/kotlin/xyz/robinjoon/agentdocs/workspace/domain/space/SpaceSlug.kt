package xyz.robinjoon.agentdocs.workspace.domain.space

// The short address of a space, used as /s/{slug}. Lowercase letters, digits, and inner hyphens keep it safe in a
// URL path without encoding.
@JvmInline
value class SpaceSlug(
    val value: String,
) {
    init {
        if (!pattern.matches(value)) {
            throw InvalidSpaceException("slug must be 1 to $MAX_LENGTH lowercase letters, digits, or inner hyphens")
        }
    }

    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH = 50
        private val pattern = Regex("[a-z0-9](?:[a-z0-9-]{0,48}[a-z0-9])?")
    }
}
