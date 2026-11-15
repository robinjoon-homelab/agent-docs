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

    @Test
    fun `a default repository is one of the linked repositories, given in any remote form`() {
        val links = listOf(repository("homelab/agent-docs"), repository("homelab/infra"))
        val linked = space.describe(SpaceProfile.of("Agent Docs", "", links))

        val chosen = linked.chooseRepository("git@github.com:Homelab/Infra.git")

        assertThat(chosen.guidance.defaultRepository).isEqualTo("homelab/infra")
        assertThat(chosen.chooseRepository(" ").guidance.defaultRepository).isNull()
        assertThatThrownBy { linked.chooseRepository("homelab/other") }
            .isInstanceOf(InvalidSpaceException::class.java)
            .hasMessage("the default repository must be one of the GitHub repositories linked to the space")
    }

    @Test
    fun `an organization link alone names no repository to default to`() {
        val links = listOf(ExternalLink.of(ExternalLinkKind.GITHUB_ORGANIZATION, "homelab"))
        val organization = space.describe(SpaceProfile.of("Agent Docs", "", links))

        assertThatThrownBy { organization.chooseRepository("homelab/agent-docs") }.isInstanceOf(InvalidSpaceException::class.java)
    }

    @Test
    fun `links without the default repository drop it and keep the guide`() {
        val guide = UUID.fromString("77777777-7777-4777-8777-777777777777")
        val links = listOf(repository("homelab/agent-docs"), repository("homelab/infra"))
        val guided = space.describe(SpaceProfile.of("Agent Docs", "", links)).guideBy(guide).chooseRepository("homelab/infra")

        val profile = SpaceProfile.of("Agent Docs", "", links.take(1), guided.guidance)

        assertThat(guided.describe(profile).guidance).isEqualTo(SpaceGuidance.of(guide, null))
        assertThat(guided.guideBy(null).guidance.guideDocumentId).isNull()
    }

    private fun repository(value: String): ExternalLink = ExternalLink.of(ExternalLinkKind.GITHUB_REPOSITORY, value)
}
