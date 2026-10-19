package xyz.robinjoon.agentdocs.workspace.application

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.application.port.output.ActivityReporter
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.agentAccess
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.context
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.now
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.ownerAccess
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceChange
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceDraft
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceLocator
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceStatistics
import xyz.robinjoon.agentdocs.workspace.application.port.input.PermissionDeniedException
import xyz.robinjoon.agentdocs.workspace.application.port.input.SpaceNotResolvedException
import xyz.robinjoon.agentdocs.workspace.application.port.output.OperationStore
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceStatisticsCatalog
import xyz.robinjoon.agentdocs.workspace.application.service.SpaceService
import xyz.robinjoon.agentdocs.workspace.application.service.WriteJournal
import xyz.robinjoon.agentdocs.workspace.domain.Permission
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.space.ExternalLink
import xyz.robinjoon.agentdocs.workspace.domain.space.ExternalLinkKind
import xyz.robinjoon.agentdocs.workspace.domain.space.SlugTakenException
import xyz.robinjoon.agentdocs.workspace.domain.space.Space
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceProfile
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import java.util.UUID

class SpaceServiceTest {
    private val spaces = mockk<SpaceRepository>()
    private val statistics = mockk<SpaceStatisticsCatalog>()
    private val activity = mockk<ActivityReporter>()
    private val service = SpaceService(spaces, statistics, WriteJournal(activity, mockk<OperationStore>(), WorkspaceFixtures.clock))
    private val homelab = space("homelab", ExternalLink.of(ExternalLinkKind.GITHUB_ORGANIZATION, "homelab"))
    private val docs =
        space(
            "agent-docs",
            ExternalLink.of(ExternalLinkKind.GITHUB_REPOSITORY, "homelab/agent-docs"),
            ExternalLink.of(ExternalLinkKind.LOCAL_PATH, "/Users/me/IdeaProjects/agent-docs"),
        )
    private val projects = space("projects", ExternalLink.of(ExternalLinkKind.LOCAL_PATH, "/Users/me/IdeaProjects"))

    init {
        every { activity.record(any()) } just runs
    }

    @Test
    fun `a repository link wins over a link to its organization`() {
        every { spaces.findAll() } returns listOf(homelab, docs)

        assertThat(service.resolve(ownerAccess, SpaceLocator("git@github.com:homelab/agent-docs.git", null))).isEqualTo(docs)
        assertThat(service.resolve(ownerAccess, SpaceLocator("https://github.com/homelab/scraping-platform", null))).isEqualTo(homelab)
    }

    @Test
    fun `the deepest linked directory wins for a path`() {
        every { spaces.findAll() } returns listOf(projects, docs)

        assertThat(service.resolve(ownerAccess, SpaceLocator(null, "/Users/me/IdeaProjects/agent-docs/frontend"))).isEqualTo(docs)
        assertThat(service.resolve(ownerAccess, SpaceLocator(null, "/Users/me/IdeaProjects/other"))).isEqualTo(projects)
    }

    @Test
    fun `an agent only finds spaces its connection lists and gets them as candidates otherwise`() {
        every { spaces.findAll() } returns listOf(homelab, docs)

        assertThatThrownBy { service.resolve(agentAccess(Permission.READ, setOf(homelab.id)), SpaceLocator(null, "/tmp/elsewhere")) }
            .isInstanceOfSatisfying(SpaceNotResolvedException::class.java) {
                assertThat(it.message).isEqualTo("no space you can see is linked to /tmp/elsewhere")
                assertThat(it.candidates).containsExactly(homelab)
            }
    }

    @Test
    fun `a slug kept for an old address cannot be taken by a new space`() {
        every { spaces.findBySlug(SpaceSlug("docs")) } returns docs

        val draft = SpaceDraft(SpaceSlug("docs"), SpaceProfile.of("Docs", "", emptyList()))

        assertThatThrownBy { service.create(context(ownerAccess), draft) }.isInstanceOf(SlugTakenException::class.java)
    }

    @Test
    fun `only the owner creates spaces`() {
        val draft = SpaceDraft(SpaceSlug("new"), SpaceProfile.of("New", "", emptyList()))

        assertThatThrownBy { service.create(context(agentAccess(Permission.EDIT, setOf(docs.id))), draft) }
            .isInstanceOf(PermissionDeniedException::class.java)
            .hasMessage("creating a space needs owner permission")
    }

    @Test
    fun `changing the slug keeps the old one as a redirect`() {
        every { spaces.findBySlugForUpdate(SpaceSlug("agent-docs")) } returns docs
        every { spaces.findBySlug(SpaceSlug("adocs")) } returns null
        every { spaces.update(any()) } answers { firstArg() }

        val changed =
            service.change(
                context(ownerAccess),
                SpaceSlug("agent-docs"),
                SpaceChange(SpaceSlug("adocs"), "Agent Docs", null, null),
            )

        assertThat(changed.slug).isEqualTo(SpaceSlug("adocs"))
        assertThat(changed.address.formerSlugs).containsExactly(SpaceSlug("agent-docs"))
        assertThat(changed.profile.name).isEqualTo("Agent Docs")
    }

    @Test
    fun `the list shows only visible spaces, most recently active first`() {
        every { spaces.findAll() } returns listOf(homelab, docs, projects)
        every { statistics.forSpaces(setOf(homelab.id, docs.id)) } returns mapOf(docs.id to SpaceStatistics(3, now))

        val listed = service.list(agentAccess(Permission.READ, setOf(homelab.id, docs.id)))

        assertThat(listed.map { it.space }).containsExactly(docs, homelab)
        assertThat(listed.first().documentCount).isEqualTo(3)
    }

    private fun space(
        slug: String,
        vararg links: ExternalLink,
    ): Space = Space.create(SpaceId(UUID.randomUUID()), SpaceSlug(slug), SpaceProfile.of(slug, "", links.toList()), now.minusSeconds(3_600))
}
