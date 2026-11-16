package xyz.robinjoon.agentdocs.workspace.application

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import xyz.robinjoon.agentdocs.workspace.application.model.AssetContentMissingException
import xyz.robinjoon.agentdocs.workspace.application.model.ExportAsset
import xyz.robinjoon.agentdocs.workspace.application.model.ExportAssetMissingException
import xyz.robinjoon.agentdocs.workspace.application.model.WorkspaceExport
import xyz.robinjoon.agentdocs.workspace.application.port.input.ExportIncompleteException
import xyz.robinjoon.agentdocs.workspace.application.port.input.PermissionDeniedException
import xyz.robinjoon.agentdocs.workspace.application.port.output.ExportArchive
import xyz.robinjoon.agentdocs.workspace.application.port.output.ExportCatalog
import xyz.robinjoon.agentdocs.workspace.application.service.ExportService
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.Actor
import xyz.robinjoon.agentdocs.workspace.domain.AgentIdentity
import xyz.robinjoon.agentdocs.workspace.domain.Permission
import xyz.robinjoon.agentdocs.workspace.domain.SpaceScope
import java.util.UUID

class ExportServiceTest {
    private val catalog = mockk<ExportCatalog>()
    private val archive = mockk<ExportArchive>()
    private val service = ExportService(catalog, archive)
    private val user = UUID.randomUUID()

    @Test
    fun `a missing export original becomes a public incomplete failure without losing its context`() {
        val asset = ExportAsset("asset-id", "html", "missing-hash")
        val missing = AssetContentMissingException(asset.sha256)
        val archiveFailure = ExportAssetMissingException(asset, missing)
        val snapshot = WorkspaceExport(emptyMap(), emptyMap(), listOf(asset))
        every { catalog.snapshot(null) } returns snapshot
        every { archive.zip(snapshot) } throws archiveFailure

        val failure =
            assertThrows<ExportIncompleteException> {
                service.export(Access(Actor.human(user, "owner"), SpaceScope.AllSpaces, Permission.OWNER), null)
            }

        assertThat(failure.failedAsset).isEqualTo(asset)
        assertThat(failure.cause).isSameAs(archiveFailure)
        assertThat(failure.cause?.cause).isSameAs(missing)
    }

    @Test
    fun `a human without owner permission cannot export`() {
        assertThatThrownBy { service.export(Access(Actor.human(user, "member"), SpaceScope.Listed(emptyMap()), Permission.READ), null) }
            .isInstanceOf(PermissionDeniedException::class.java)
    }

    @Test
    fun `a missing projection space preserves the public not found failure and its cause`() {
        val slug =
            xyz.robinjoon.agentdocs.workspace.domain.space
                .SpaceSlug("missing")
        val failure =
            xyz.robinjoon.agentdocs.workspace.application.model
                .ExportSpaceUnavailableException(slug)
        every { catalog.snapshot(slug) } throws failure
        assertThatThrownBy { service.export(Access(Actor.human(user, "owner"), SpaceScope.AllSpaces, Permission.OWNER), slug) }
            .isInstanceOf(xyz.robinjoon.agentdocs.workspace.application.port.input.SpaceNotFoundException::class.java)
            .hasCause(failure)
    }

    @Test
    fun `human owner receives the serialized snapshot`() {
        val snapshot = WorkspaceExport(emptyMap(), emptyMap(), emptyList())
        every { catalog.snapshot(null) } returns snapshot
        every { archive.zip(snapshot) } returns byteArrayOf(1, 2)
        assertThat(service.export(Access(Actor.human(user, "owner"), SpaceScope.AllSpaces, Permission.OWNER), null))
            .containsExactly(1, 2)
    }

    @Test
    fun `agents cannot export with their connection permission`() {
        val actor = Actor.agent(user, "owner", AgentIdentity(UUID.randomUUID(), "tool"))
        assertThatThrownBy { service.export(Access(actor, SpaceScope.Listed(emptyMap()), Permission.EDIT), null) }
            .isInstanceOf(PermissionDeniedException::class.java)
    }
}
