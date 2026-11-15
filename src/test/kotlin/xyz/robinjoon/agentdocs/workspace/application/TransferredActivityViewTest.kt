package xyz.robinjoon.agentdocs.workspace.application

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.application.model.ActivityActor
import xyz.robinjoon.agentdocs.application.model.ActivityEntry
import xyz.robinjoon.agentdocs.application.model.ActivityQuery
import xyz.robinjoon.agentdocs.application.model.ActivityRecord
import xyz.robinjoon.agentdocs.application.model.ActivitySubject
import xyz.robinjoon.agentdocs.application.port.output.ActivityCatalog
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceRepository
import xyz.robinjoon.agentdocs.workspace.application.service.ActivityService
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath

class TransferredActivityViewTest {
    @Test
    fun `an old source activity is retained without enrichment from the documents new destination`() {
        val original = WorkspaceFixtures.document(emptyList(), 2)
        val moved = original.transfer(WorkspaceFixtures.otherSpaceId, DocumentPath("private/note"), WorkspaceFixtures.now)
        val entry =
            ActivityEntry(
                1,
                ActivityRecord(
                    "document.created",
                    ActivityActor.system,
                    ActivitySubject(original.spaceId.value, original.id.value, null, 2),
                    "Original summary",
                    WorkspaceFixtures.now,
                ),
            )
        val catalog = mockk<ActivityCatalog>()
        val documents = mockk<DocumentRepository>()
        val spaces = mockk<SpaceRepository>()
        every { spaces.findBySlug(WorkspaceFixtures.space.slug) } returns WorkspaceFixtures.space
        every { catalog.find(ActivityQuery(setOf(original.spaceId.value), null, null, 20, false)) } returns listOf(entry)
        every { spaces.findByIds(setOf(original.spaceId)) } returns listOf(WorkspaceFixtures.space)
        every { documents.findByIds(listOf(original.id)) } returns listOf(moved)
        val result =
            ActivityService(catalog, spaces, documents)
                .recent(WorkspaceFixtures.ownerAccess, WorkspaceFixtures.space.slug, 20)
                .single()
        assertThat(result.entry).isEqualTo(entry)
        assertThat(result.space?.id).isEqualTo(original.spaceId)
        assertThat(result.documentTitle).isNull()
    }
}
