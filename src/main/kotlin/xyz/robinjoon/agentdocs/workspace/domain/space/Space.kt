package xyz.robinjoon.agentdocs.workspace.domain.space

import xyz.robinjoon.agentdocs.domain.AggregateRoot
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import java.time.Instant

// An isolation boundary the owner defines. It exists on its own, independent of GitHub or any other service, and every
// document, thread, and asset belongs to exactly one space. The private constructor and copy keep changes behind
// create, restore, and the setting methods. An archived space drops out of space lists, search across spaces, and an
// agent's space lookup, and still opens at its address.
@AggregateRoot
@ConsistentCopyVisibility
data class Space private constructor(
    val id: SpaceId,
    val address: SpaceAddress,
    val profile: SpaceProfile,
    val lifecycle: SpaceLifecycle,
) {
    val slug: SpaceSlug get() = address.slug

    val createdAt: Instant get() = lifecycle.createdAt

    val isArchived: Boolean get() = lifecycle.archivedAt != null

    fun moveTo(slug: SpaceSlug): Space = copy(address = address.movedTo(slug))

    fun describe(profile: SpaceProfile): Space = copy(profile = profile)

    fun archive(at: Instant): Space = if (isArchived) this else copy(lifecycle = lifecycle.copy(archivedAt = at))

    fun unarchive(): Space = copy(lifecycle = lifecycle.copy(archivedAt = null))

    companion object {
        fun create(
            id: SpaceId,
            slug: SpaceSlug,
            profile: SpaceProfile,
            createdAt: Instant,
        ): Space = Space(id, SpaceAddress.of(slug, emptySet()), profile, SpaceLifecycle(createdAt, null))

        fun restore(
            id: SpaceId,
            address: SpaceAddress,
            profile: SpaceProfile,
            lifecycle: SpaceLifecycle,
        ): Space = Space(id, address, profile, lifecycle)
    }
}
