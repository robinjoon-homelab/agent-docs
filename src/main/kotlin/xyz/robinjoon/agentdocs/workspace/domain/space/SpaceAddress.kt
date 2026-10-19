package xyz.robinjoon.agentdocs.workspace.domain.space

import java.util.Collections

// The current slug and the slugs the space used before. An old address keeps redirecting to the space, so a former
// slug stays reserved for it and can be taken back later.
@ConsistentCopyVisibility
data class SpaceAddress private constructor(
    val slug: SpaceSlug,
    val formerSlugs: Set<SpaceSlug>,
) {
    init {
        if (slug in formerSlugs) {
            throw InvalidSpaceException("the current slug cannot also be a former slug")
        }
        if (formerSlugs.size > MAX_FORMER_SLUGS) {
            throw InvalidSpaceException("a space keeps at most $MAX_FORMER_SLUGS former slugs")
        }
    }

    fun movedTo(newSlug: SpaceSlug): SpaceAddress =
        if (newSlug == slug) this else SpaceAddress(newSlug, snapshot(formerSlugs - newSlug + slug))

    companion object {
        const val MAX_FORMER_SLUGS = 50

        fun of(
            slug: SpaceSlug,
            formerSlugs: Set<SpaceSlug>,
        ): SpaceAddress = SpaceAddress(slug, snapshot(formerSlugs))

        private fun snapshot(slugs: Set<SpaceSlug>): Set<SpaceSlug> = Collections.unmodifiableSet(LinkedHashSet(slugs))
    }
}
