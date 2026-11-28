package xyz.robinjoon.agentdocs.workspace.application

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.application.model.ActivityActor
import xyz.robinjoon.agentdocs.application.model.ActivityEntry
import xyz.robinjoon.agentdocs.application.model.ActivityFilter
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
import xyz.robinjoon.agentdocs.workspace.application.port.input.SpaceNotFoundException
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
        every {
            catalog.find(
                ActivityQuery(setOf(spaceId.value), null, 50, oldestFirst = true, filter = ActivityFilter(since = since)),
            )
        } returns
            listOf(visible, outside)
        every { spaces.findByIds(setOf(spaceId)) } returns listOf(space)
        every { documents.findByIds(emptyList()) } returns emptyList()

        val feed = service.changesSince(agentAccess(Permission.READ, setOf(spaceId)), since, 50)

        assertThat(feed.entries.map { it.entry }).containsExactly(visible)
        assertThat(feed.entries.single().space).isEqualTo(space)
        assertThat(feed.nextSince).isEqualTo(outside.record.at)
        assertThat(feed.initialSince).isEqualTo(since)
        assertThat(feed.nextCursor).isEqualTo(2)
    }

    @Test
    fun `the whole log is for the owner only`() {
        assertThatThrownBy { service.recent(agentAccess(Permission.EDIT, setOf(spaceId, otherSpaceId)), null, 20) }
            .isInstanceOf(PermissionDeniedException::class.java)
            .hasMessage("reading the whole activity log needs owner permission")
    }

    @Test
    fun `a filter cannot grant access to an unlisted space`() {
        every { spaces.findBySlug(space.slug) } returns space
        val access = agentAccess(Permission.READ, setOf(otherSpaceId))
        val filter = ActivityFilter(kind = "document.revised", since = now)

        assertThatThrownBy { service.recent(access, space.slug, 20, filter) }
            .isInstanceOf(SpaceNotFoundException::class.java)
    }

    @Test
    fun `101 equal timestamp changes continue through 100 and one then an empty page without skipping`() {
        val since = now.minusSeconds(1)
        val entries = (1L..101L).map { entry(it, ActivitySubject(spaceId.value, null, null, null), now) }
        every { catalog.find(ActivityQuery(setOf(spaceId.value), null, 100, true, filter = ActivityFilter(since = since))) } returns
            entries.take(100)
        every {
            catalog.find(
                ActivityQuery(setOf(spaceId.value), null, 100, true, afterId = 100, filter = ActivityFilter(since = since)),
            )
        } returns
            entries.drop(100)
        every {
            catalog.find(
                ActivityQuery(setOf(spaceId.value), null, 100, true, afterId = 101, filter = ActivityFilter(since = since)),
            )
        } returns
            emptyList()
        every { spaces.findByIds(setOf(spaceId)) } returns listOf(space)
        every { spaces.findByIds(emptySet()) } returns emptyList()
        every { documents.findByIds(emptyList()) } returns emptyList()
        val access = agentAccess(Permission.READ, setOf(spaceId))

        val first = service.changesSince(access, since, 100)
        val second = service.changesSince(access, first.initialSince, 100, first.nextCursor)
        val end = service.changesSince(access, second.initialSince, 100, second.nextCursor)

        assertThat((first.entries + second.entries).map { it.entry.id }).containsExactlyElementsOf(1L..101L)
        assertThat(first.nextSince).isEqualTo(now)
        assertThat(second.nextSince).isEqualTo(now)
        assertThat(first.initialSince).isEqualTo(since)
        assertThat(second.initialSince).isEqualTo(since)
        assertThat(second.nextCursor).isEqualTo(101)
        assertThat(end.entries).isEmpty()
        assertThat(end.nextCursor).isEqualTo(101)
        assertThat(end.nextSince).isEqualTo(since)
        assertThat(end.initialSince).isEqualTo(since)
    }

    @Test
    fun `legacy timestamp-only consumers advance using nextSince`() {
        val since = now.minusSeconds(10)
        val firstEntry = entry(1, ActivitySubject(spaceId.value, null, null, null), now.minusSeconds(5))
        val secondEntry = entry(2, ActivitySubject(spaceId.value, null, null, null), now)
        every { catalog.find(ActivityQuery(setOf(spaceId.value), null, 1, true, filter = ActivityFilter(since = since))) } returns
            listOf(firstEntry)
        every {
            catalog.find(
                ActivityQuery(setOf(spaceId.value), null, 1, true, filter = ActivityFilter(since = firstEntry.record.at)),
            )
        } returns
            listOf(secondEntry)
        every { spaces.findByIds(setOf(spaceId)) } returns listOf(space)
        every { documents.findByIds(emptyList()) } returns emptyList()
        val access = agentAccess(Permission.READ, setOf(spaceId))

        val first = service.changesSince(access, since, 1)
        val second = service.changesSince(access, first.nextSince, 1)

        assertThat(
            first.entries
                .single()
                .entry.id,
        ).isEqualTo(1)
        assertThat(first.nextSince).isEqualTo(firstEntry.record.at).isAfter(since)
        assertThat(
            second.entries
                .single()
                .entry.id,
        ).isEqualTo(2)
        assertThat(second.nextSince).isEqualTo(now).isAfter(first.nextSince)
        assertThat(second.initialSince).isEqualTo(first.nextSince)
    }

    private fun entry(
        id: Long,
        subject: ActivitySubject,
        at: Instant,
    ): ActivityEntry = ActivityEntry(id, ActivityRecord("document.revised", ActivityActor.system, subject, "summary", at))
}
