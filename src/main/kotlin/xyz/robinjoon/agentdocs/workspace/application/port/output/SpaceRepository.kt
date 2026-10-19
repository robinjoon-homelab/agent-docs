package xyz.robinjoon.agentdocs.workspace.application.port.output

import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.space.Space
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug

// add and update return the stored state read back after writing.
interface SpaceRepository {
    // Fails with SlugTakenException when another space holds the slug as its current or a former slug.
    fun add(space: Space): Space

    // Writes the address, profile, and links of a space loaded for update in the same transaction.
    fun update(space: Space): Space

    fun findById(id: SpaceId): Space?

    // Matches the current slug and every former slug, so an old address still finds the space.
    fun findBySlug(slug: SpaceSlug): Space?

    fun findBySlugForUpdate(slug: SpaceSlug): Space?

    fun findAll(): List<Space>

    fun findByIds(ids: Set<SpaceId>): List<Space>
}
