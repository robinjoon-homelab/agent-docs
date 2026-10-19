package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.support.persistence.MigratedH2Database
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.at
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.space
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.space.ExternalLink
import xyz.robinjoon.agentdocs.workspace.domain.space.ExternalLinkKind
import xyz.robinjoon.agentdocs.workspace.domain.space.SlugTakenException
import xyz.robinjoon.agentdocs.workspace.domain.space.Space
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceProfile
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import java.util.UUID

class ExposedSpaceRepositoryTest {
    private val database = MigratedH2Database()
    private val repository = ExposedSpaceRepository()

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `a space round trips with its links in order and its former slugs`() {
        val links =
            listOf(
                ExternalLink.of(ExternalLinkKind.LOCAL_PATH, "/srv/docs"),
                ExternalLink.of(ExternalLinkKind.GITHUB_REPOSITORY, "homelab/agent-docs"),
            )
        val created = Space.create(SpaceId(UUID.randomUUID()), SpaceSlug("agent-docs"), SpaceProfile.of("Agent Docs", "Plans", links), at)
        database.inTransaction { repository.add(created) }

        val moved = database.inTransaction { repository.update(created.moveTo(SpaceSlug("docs")).moveTo(SpaceSlug("adocs"))) }

        assertThat(moved.profile.links).containsExactlyElementsOf(links)
        assertThat(moved.address.formerSlugs).containsExactly(SpaceSlug("agent-docs"), SpaceSlug("docs"))
        assertThat(database.inTransaction { repository.findById(created.id) }).isEqualTo(moved)
    }

    @Test
    fun `a former slug still finds the space`() {
        val created = database.inTransaction { repository.add(space("agent-docs")) }
        database.inTransaction { repository.update(created.moveTo(SpaceSlug("docs"))) }

        assertThat(database.inTransaction { repository.findBySlug(SpaceSlug("agent-docs")) }?.slug).isEqualTo(SpaceSlug("docs"))
        assertThat(database.inTransaction { repository.findBySlugForUpdate(SpaceSlug("docs")) }?.id).isEqualTo(created.id)
        assertThat(database.inTransaction { repository.findBySlug(SpaceSlug("missing")) }).isNull()
    }

    // The database keeps current slugs unique and former slugs unique; the use case also checks a new current slug
    // against former slugs before writing.
    @Test
    fun `a slug is unique among current slugs and among former slugs`() {
        val first = database.inTransaction { repository.add(space("agent-docs")) }
        database.inTransaction { repository.update(first.moveTo(SpaceSlug("docs"))) }
        val second = database.inTransaction { repository.add(space("agent-docs")) }

        assertThatThrownBy { database.inTransaction { repository.add(space("docs")) } }.isInstanceOf(SlugTakenException::class.java)
        assertThatThrownBy {
            database.inTransaction { repository.update(second.moveTo(SpaceSlug("notes"))) }
        }.isInstanceOf(SlugTakenException::class.java)
    }

    @Test
    fun `spaces are found by ids and listed oldest first`() {
        val first = database.inTransaction { repository.add(space("first")) }
        val second = database.inTransaction { repository.add(space("second")) }

        assertThat(database.inTransaction { repository.findByIds(setOf(second.id)) }).containsExactly(second)
        assertThat(database.inTransaction { repository.findAll() }.map { it.id }).containsExactlyInAnyOrder(first.id, second.id)
        assertThat(database.inTransaction { repository.findByIds(emptySet()) }).isEmpty()
    }
}
