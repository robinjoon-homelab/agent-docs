package xyz.robinjoon.agentdocs.workspace.domain.space

import xyz.robinjoon.agentdocs.domain.AggregateRoot
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import java.time.Instant

// An isolation boundary the owner defines. It exists on its own, independent of GitHub or any other service, and every
// document, thread, and asset belongs to exactly one space. The private constructor and copy keep changes behind
// create, restore, and the setting methods.
@AggregateRoot
@ConsistentCopyVisibility
data class Space private constructor(
    val id: SpaceId,
    val address: SpaceAddress,
    val profile: SpaceProfile,
    val createdAt: Instant,
) {
    val slug: SpaceSlug get() = address.slug

    fun moveTo(slug: SpaceSlug): Space = copy(address = address.movedTo(slug))

    fun describe(profile: SpaceProfile): Space = copy(profile = profile)

    companion object {
        fun create(
            id: SpaceId,
            slug: SpaceSlug,
            profile: SpaceProfile,
            createdAt: Instant,
        ): Space = Space(id, SpaceAddress.of(slug, emptySet()), profile, createdAt)

        fun restore(
            id: SpaceId,
            address: SpaceAddress,
            profile: SpaceProfile,
            createdAt: Instant,
        ): Space = Space(id, address, profile, createdAt)
    }
}
