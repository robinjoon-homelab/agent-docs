package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceRepository
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.space.ExternalLink
import xyz.robinjoon.agentdocs.workspace.domain.space.SlugTakenException
import xyz.robinjoon.agentdocs.workspace.domain.space.Space
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceAddress
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceLifecycle
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceProfile
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug

class ExposedSpaceRepository : SpaceRepository {
    // Ids are random UUIDs, so a unique violation means the slug is taken, by a current or a former slug.
    override fun add(space: Space): Space {
        try {
            SpacesTable.insert {
                it[id] = space.id.value
                it[slug] = space.slug.value
                it[name] = space.profile.name
                it[description] = space.profile.description
                it[createdAt] = space.createdAt.utc()
                it[archivedAt] = space.lifecycle.archivedAt?.utc()
            }
            writeChildren(space)
        } catch (exception: ExposedSQLException) {
            if (exception.isUniqueViolation()) throw SlugTakenException(space.slug, exception)
            throw exception
        }
        return selectById(space.id).single().toSpace()
    }

    // Former slugs and links are rewritten whole: both are small lists that change together with the settings.
    override fun update(space: Space): Space {
        try {
            val updated =
                SpacesTable.update({ SpacesTable.id eq space.id.value }) {
                    it[slug] = space.slug.value
                    it[name] = space.profile.name
                    it[description] = space.profile.description
                    it[archivedAt] = space.lifecycle.archivedAt?.utc()
                }
            check(updated == 1) { "Space to update does not exist: ${space.id}" }
            SpaceFormerSlugsTable.deleteWhere { SpaceFormerSlugsTable.spaceId eq space.id.value }
            SpaceLinksTable.deleteWhere { SpaceLinksTable.spaceId eq space.id.value }
            writeChildren(space)
        } catch (exception: ExposedSQLException) {
            if (exception.isUniqueViolation()) throw SlugTakenException(space.slug, exception)
            throw exception
        }
        return selectById(space.id).single().toSpace()
    }

    override fun findById(id: SpaceId): Space? = selectById(id).singleOrNull()?.toSpace()

    override fun findBySlug(slug: SpaceSlug): Space? = idOf(slug)?.let { selectById(it).singleOrNull()?.toSpace() }

    override fun findBySlugForUpdate(slug: SpaceSlug): Space? = idOf(slug)?.let { selectById(it).forUpdate().singleOrNull()?.toSpace() }

    override fun findAll(): List<Space> = SpacesTable.selectAll().orderBy(SpacesTable.createdAt).map { it.toSpace() }

    override fun findByIds(ids: Set<SpaceId>): List<Space> =
        if (ids.isEmpty()) {
            emptyList()
        } else {
            SpacesTable
                .selectAll()
                .where {
                    SpacesTable.id inList ids.map { it.value }
                }.map { it.toSpace() }
        }

    private fun idOf(slug: SpaceSlug): SpaceId? {
        val current =
            SpacesTable
                .select(SpacesTable.id)
                .where { SpacesTable.slug eq slug.value }
                .singleOrNull()
                ?.get(SpacesTable.id)
        val former =
            current ?: SpaceFormerSlugsTable
                .select(SpaceFormerSlugsTable.spaceId)
                .where { SpaceFormerSlugsTable.slug eq slug.value }
                .singleOrNull()
                ?.get(SpaceFormerSlugsTable.spaceId)
        return former?.let(::SpaceId)
    }

    private fun writeChildren(space: Space) {
        SpaceFormerSlugsTable.batchInsert(
            space.address.formerSlugs.withIndex(),
            shouldReturnGeneratedValues = false,
        ) { (position, former) ->
            this[SpaceFormerSlugsTable.slug] = former.value
            this[SpaceFormerSlugsTable.spaceId] = space.id.value
            this[SpaceFormerSlugsTable.position] = position
        }
        SpaceLinksTable.batchInsert(space.profile.links.withIndex(), shouldReturnGeneratedValues = false) { (position, link) ->
            this[SpaceLinksTable.spaceId] = space.id.value
            this[SpaceLinksTable.position] = position
            this[SpaceLinksTable.kind] = link.kind
            this[SpaceLinksTable.value] = link.value
        }
    }

    private fun selectById(id: SpaceId): Query = SpacesTable.selectAll().where { SpacesTable.id eq id.value }

    private fun ResultRow.toSpace(): Space {
        val id = this[SpacesTable.id]
        val former =
            SpaceFormerSlugsTable
                .selectAll()
                .where { SpaceFormerSlugsTable.spaceId eq id }
                .orderBy(SpaceFormerSlugsTable.position to SortOrder.ASC)
                .map { SpaceSlug(it[SpaceFormerSlugsTable.slug]) }
        val links =
            SpaceLinksTable
                .selectAll()
                .where { SpaceLinksTable.spaceId eq id }
                .orderBy(SpaceLinksTable.position to SortOrder.ASC)
                .map { ExternalLink.of(it[SpaceLinksTable.kind], it[SpaceLinksTable.value]) }
        val profile = SpaceProfile.of(this[SpacesTable.name], this[SpacesTable.description], links)
        return Space.restore(
            SpaceId(id),
            SpaceAddress.of(SpaceSlug(this[SpacesTable.slug]), former.toSet()),
            profile,
            SpaceLifecycle(this[SpacesTable.createdAt].toInstant(), this[SpacesTable.archivedAt]?.toInstant()),
        )
    }
}
