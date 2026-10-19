package xyz.robinjoon.agentdocs.workspace.domain.space

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import java.time.Instant
import java.util.UUID

class SpaceTest {
    private val id = SpaceId(UUID.fromString("3c1d2e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f"))
    private val createdAt = Instant.parse("2026-10-18T00:00:00Z")
    private val profile = SpaceProfile.of("Agent Docs", "Planning and design docs", emptyList())
    private val space = Space.create(id, SpaceSlug("agent-docs"), profile, createdAt)

    @Test
    fun `a new space has no former slugs`() {
        assertThat(space.slug).isEqualTo(SpaceSlug("agent-docs"))
        assertThat(space.address.formerSlugs).isEmpty()
    }

    @Test
    fun `moving keeps the old slug reserved so old links keep redirecting`() {
        val moved = space.moveTo(SpaceSlug("docs")).moveTo(SpaceSlug("platform-docs"))

        assertThat(moved.slug).isEqualTo(SpaceSlug("platform-docs"))
        assertThat(moved.address.formerSlugs).containsExactly(SpaceSlug("agent-docs"), SpaceSlug("docs"))
    }

    @Test
    fun `moving back to a former slug takes it out of the former slugs`() {
        val returned = space.moveTo(SpaceSlug("docs")).moveTo(SpaceSlug("agent-docs"))

        assertThat(returned.slug).isEqualTo(SpaceSlug("agent-docs"))
        assertThat(returned.address.formerSlugs).containsExactly(SpaceSlug("docs"))
    }

    @Test
    fun `moving to the current slug changes nothing`() {
        assertThat(space.moveTo(SpaceSlug("agent-docs"))).isEqualTo(space)
    }

    @ParameterizedTest
    @ValueSource(strings = ["a", "agent-docs", "2026-q4"])
    fun `slugs of lowercase letters, digits, and inner hyphens are accepted`(slug: String) {
        assertThat(SpaceSlug(slug).value).isEqualTo(slug)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "-docs", "docs-", "Agent", "agent_docs", "agent docs"])
    fun `other slugs are rejected`(slug: String) {
        assertThatThrownBy { SpaceSlug(slug) }
            .isInstanceOf(InvalidSpaceException::class.java)
            .hasMessage("slug must be 1 to 50 lowercase letters, digits, or inner hyphens")
    }

    @Test
    fun `the profile trims its texts and keeps a link only once`() {
        val link = ExternalLink.of(ExternalLinkKind.GITHUB_REPOSITORY, "homelab/agent-docs")

        assertThat(SpaceProfile.of("  Agent Docs ", " docs ", listOf(link)))
            .isEqualTo(SpaceProfile.of("Agent Docs", "docs", listOf(link)))
        assertThatThrownBy {
            SpaceProfile.of("Agent Docs", "", listOf(link, ExternalLink.of(ExternalLinkKind.GITHUB_REPOSITORY, "Homelab/Agent-Docs")))
        }.isInstanceOf(InvalidSpaceException::class.java)
            .hasMessage("each link may appear only once")
    }

    @Test
    fun `a blank name is rejected`() {
        assertThatThrownBy { SpaceProfile.of("   ", "", emptyList()) }
            .isInstanceOf(InvalidSpaceException::class.java)
            .hasMessage("name must be 1 to 100 characters")
    }
}
