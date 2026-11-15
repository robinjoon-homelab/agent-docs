package xyz.robinjoon.agentdocs.workspace.application

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentEventSnapshot
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentEventCatalog
import xyz.robinjoon.agentdocs.workspace.application.service.DocumentEventsService
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.Actor
import xyz.robinjoon.agentdocs.workspace.domain.Permission
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.SpaceScope
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import java.util.UUID

class DocumentEventsServiceTest {
    private val catalog = mockk<DocumentEventCatalog>()
    private val service = DocumentEventsService(catalog)
    private val id = DocumentId(UUID.randomUUID())
    private val space = SpaceId(UUID.randomUUID())
    private val access = Access(Actor.human(UUID.randomUUID(), "owner"), SpaceScope.Listed(setOf(space)), Permission.READ)

    @Test
    fun `a committed snapshot includes pending version and independent thread state`() {
        val snapshot = DocumentEventSnapshot(space, 12, 8, false, "thread-state")
        every { catalog.snapshot(id) } returns snapshot
        assertThat(service.snapshot(access, id)).isEqualTo(snapshot)
    }

    @Test
    fun `moving a document outside the current grant hides subsequent snapshots`() {
        every { catalog.snapshot(id) } returns DocumentEventSnapshot(SpaceId(UUID.randomUUID()), 13, 9, false, "state")
        assertThatThrownBy { service.snapshot(access, id) }.isInstanceOf(DocumentNotFoundException::class.java)
    }

    @Test
    fun `a destroyed document terminates its readable projection`() {
        every { catalog.snapshot(id) } returns null
        assertThatThrownBy { service.snapshot(access, id) }.isInstanceOf(DocumentNotFoundException::class.java)
    }
}
