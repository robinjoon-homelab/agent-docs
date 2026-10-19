package xyz.robinjoon.agentdocs.workspace.application

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.application.model.ActivityActor
import xyz.robinjoon.agentdocs.application.model.ActivityEntry
import xyz.robinjoon.agentdocs.application.model.ActivityQuery
import xyz.robinjoon.agentdocs.application.model.ActivityRecord
import xyz.robinjoon.agentdocs.application.model.ActivitySubject
import xyz.robinjoon.agentdocs.application.port.output.ActivityCatalog
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.agentAccess
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.now
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.otherSpaceId
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.space
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.spaceId
import xyz.robinjoon.agentdocs.workspace.application.port.input.PermissionDeniedException
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceRepository
import xyz.robinjoon.agentdocs.workspace.application.service.ActivityService
import xyz.robinjoon.agentdocs.workspace.domain.Permission
import java.time.Instant

class ActivityServiceTest {
    private val catalog = mockk<ActivityCatalog>()
    private val spaces = mockk<SpaceRepository>()
    private val documents = mockk<DocumentRepository>()
    private val service = ActivityService(catalog, spaces, documents)

    @Test
    fun `an agent's feed continues from a moment and keeps only its spaces`() {
        val since = now.minusSeconds(600)
        val visible = entry(1, ActivitySubject(spaceId.value, null, null, null), now.minusSeconds(300))
        val outside = entry(2, ActivitySubject(null, null, null, null), now.minusSeconds(200))
        every { catalog.find(ActivityQuery(setOf(spaceId.value), since, null, 50, oldestFirst = true)) } returns listOf(visible, outside)
        every { spaces.findByIds(setOf(spaceId)) } returns listOf(space)
        every { documents.findByIds(emptyList()) } returns emptyList()

        val feed = service.changesSince(agentAccess(Permission.READ, setOf(spaceId)), since, 50)

        assertThat(feed.entries.map { it.entry }).containsExactly(visible)
        assertThat(feed.entries.single().space).isEqualTo(space)
        assertThat(feed.nextSince).isEqualTo(now.minusSeconds(200))
    }

    @Test
    fun `the whole log is for the owner only`() {
        assertThatThrownBy { service.recent(agentAccess(Permission.EDIT, setOf(spaceId, otherSpaceId)), null, 20) }
            .isInstanceOf(PermissionDeniedException::class.java)
            .hasMessage("reading the whole activity log needs owner permission")
    }

    private fun entry(
        id: Long,
        subject: ActivitySubject,
        at: Instant,
    ): ActivityEntry = ActivityEntry(id, ActivityRecord("document.revised", ActivityActor.system, subject, "summary", at))
}
