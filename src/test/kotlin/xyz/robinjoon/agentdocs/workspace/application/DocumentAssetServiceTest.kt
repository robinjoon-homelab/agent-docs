package xyz.robinjoon.agentdocs.workspace.application

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentAssetBinding
import xyz.robinjoon.agentdocs.workspace.application.model.DrawingText
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.VerifiedScene
import xyz.robinjoon.agentdocs.workspace.application.port.input.AssetNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.output.AssetRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.AssetStore
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentAssetStore
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.SceneVerifier
import xyz.robinjoon.agentdocs.workspace.application.service.DocumentAssetService
import xyz.robinjoon.agentdocs.workspace.domain.Permission
import xyz.robinjoon.agentdocs.workspace.domain.asset.Asset
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetContent
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetMediaType
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetOrigin
import java.util.UUID

class DocumentAssetServiceTest {
    private val documents = mockk<DocumentRepository>()
    private val references = mockk<DocumentAssetStore>()
    private val assets = mockk<AssetRepository>()
    private val bytes = mockk<AssetStore>()
    private val scenes = mockk<SceneVerifier>()
    private val service = DocumentAssetService(documents, references, assets, bytes, scenes)
    private val current = WorkspaceFixtures.document(emptyList(), 2)
    private val original = AssetId(UUID.randomUUID())
    private val copy =
        Asset.upload(
            AssetId(UUID.randomUUID()),
            current.spaceId,
            AssetContent.of(
                AssetMediaType.HTML,
                "<p>preview</p>".toByteArray(),
            ),
            AssetOrigin(
                "preview.html",
                null,
                WorkspaceFixtures.owner,
                WorkspaceFixtures.now,
            ),
        )

    @Test
    fun `a real logical reference returns its destination copy through the current document boundary`() {
        every { documents.findById(current.id) } returns current
        every { references.references(current.id) } returns setOf(original)
        every { references.bindings(setOf(current.id)) } returns listOf(DocumentAssetBinding(current.id, original, copy.id))
        every { assets.findById(copy.id) } returns copy
        every { bytes.get(copy.content.sha256) } returns "<p>preview</p>".toByteArray()
        val result = service.content(WorkspaceFixtures.ownerAccess, current.id, original)
        assertThat(result.asset).isEqualTo(copy)
        assertThat(result.bytes.toString(Charsets.UTF_8)).isEqualTo("<p>preview</p>")
    }

    @Test
    fun `arbitrary asset ids and invisible documents cannot bypass the document boundary`() {
        every { documents.findById(current.id) } returns current
        every { references.references(current.id) } returns emptySet()
        assertThatThrownBy {
            service.content(
                WorkspaceFixtures.ownerAccess,
                current.id,
                original,
            )
        }.isInstanceOf(AssetNotFoundException::class.java)
        assertThatThrownBy {
            service.content(
                WorkspaceFixtures.agentAccess(
                    Permission.READ,
                    emptySet(),
                ),
                current.id,
                original,
            )
        }.isInstanceOf(DocumentNotFoundException::class.java)
    }

    private fun stored(
        type: AssetMediaType,
        text: String,
    ): Asset {
        val asset =
            Asset.upload(
                AssetId(UUID.randomUUID()),
                current.spaceId,
                AssetContent.of(type, text.toByteArray()),
                AssetOrigin("draw", null, WorkspaceFixtures.owner, WorkspaceFixtures.now),
            )
        every { documents.findById(current.id) } returns current
        every { references.references(current.id) } returns setOf(asset.id)
        every { references.bindings(setOf(current.id)) } returns emptyList()
        every { assets.findById(asset.id) } returns asset
        every { bytes.get(asset.content.sha256) } returns text.toByteArray()
        return asset
    }

    @Test
    fun `a drawing scene's source is its stored text with what it contains, and an SVG is its text alone`() {
        val sceneText = """{"type":"excalidraw","elements":[{"id":"t","type":"text","text":"흐름"}]}"""
        val verified = VerifiedScene(1, 0, listOf(DrawingText("t", "흐름", null)))
        val scene = stored(AssetMediaType.EXCALIDRAW, sceneText)
        every { scenes.verify(any()) } returns verified

        val sceneSource = service.source(WorkspaceFixtures.ownerAccess, current.id, scene.id)
        val svg = stored(AssetMediaType.SVG, "<svg xmlns=\"http://www.w3.org/2000/svg\"/>")
        val svgSource = service.source(WorkspaceFixtures.ownerAccess, current.id, svg.id)

        assertThat(sceneSource.text).isEqualTo(sceneText)
        assertThat(sceneSource.scene).isEqualTo(verified)
        assertThat(svgSource.text).isEqualTo("<svg xmlns=\"http://www.w3.org/2000/svg\"/>")
        assertThat(svgSource.scene).isNull()
    }

    @Test
    fun `an image or a download has no source to read`() {
        val png = stored(AssetMediaType.PNG, "png")

        assertThatThrownBy { service.source(WorkspaceFixtures.ownerAccess, current.id, png.id) }
            .isInstanceOf(InvalidRequestException::class.java)
    }
}
