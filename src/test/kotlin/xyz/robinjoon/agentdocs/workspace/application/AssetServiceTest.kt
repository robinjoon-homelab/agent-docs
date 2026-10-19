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
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.owner
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.ownerAccess
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.space
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.spaceId
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.RemoteImage
import xyz.robinjoon.agentdocs.workspace.application.model.UploadedFile
import xyz.robinjoon.agentdocs.workspace.application.model.VerifiedImage
import xyz.robinjoon.agentdocs.workspace.application.port.input.AssetNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.output.AssetRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.AssetStore
import xyz.robinjoon.agentdocs.workspace.application.port.output.ImageVerifier
import xyz.robinjoon.agentdocs.workspace.application.port.output.OperationStore
import xyz.robinjoon.agentdocs.workspace.application.port.output.RemoteImageSource
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceRepository
import xyz.robinjoon.agentdocs.workspace.application.service.AssetService
import xyz.robinjoon.agentdocs.workspace.application.service.WriteJournal
import xyz.robinjoon.agentdocs.workspace.domain.Permission
import xyz.robinjoon.agentdocs.workspace.domain.asset.Asset
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetContent
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetMediaType
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetOrigin
import xyz.robinjoon.agentdocs.workspace.domain.asset.InvalidAssetException
import java.util.UUID

class AssetServiceTest {
    private val spaces = mockk<SpaceRepository>()
    private val assets = mockk<AssetRepository>()
    private val store = mockk<AssetStore>()
    private val verifier = mockk<ImageVerifier>()
    private val remote = mockk<RemoteImageSource>()
    private val activity = mockk<ActivityReporter>()
    private val service =
        AssetService(spaces, assets, store, verifier, remote, WriteJournal(activity, mockk<OperationStore>(), WorkspaceFixtures.clock))
    private val png = byteArrayOf(1, 2, 3)
    private val content = AssetContent.of(AssetMediaType.PNG, png)

    init {
        every { spaces.findBySlug(space.slug) } returns space
        every { activity.record(any()) } just runs
    }

    @Test
    fun `an upload keeps the file under its content hash with the uploader and name`() {
        every { verifier.verify(png) } returns VerifiedImage(AssetMediaType.PNG, png)
        every { assets.findByContent(spaceId, content.sha256) } returns null
        every { store.put(content.sha256, "image/png", png) } just runs
        every { assets.add(any()) } answers { firstArg() }

        val asset = service.upload(context(ownerAccess), space.slug, UploadedFile("C:\\shots\\flow.png", "image/png", png))

        assertThat(asset.content).isEqualTo(content)
        assertThat(asset.origin).isEqualTo(AssetOrigin("flow.png", null, owner, now))
    }

    @Test
    fun `the same bytes in the same space return the asset already there`() {
        val existing =
            Asset.upload(
                AssetId(UUID.randomUUID()),
                spaceId,
                content,
                AssetOrigin("first.png", null, owner, now.minusSeconds(60)),
            )
        every { verifier.verify(png) } returns VerifiedImage(AssetMediaType.PNG, png)
        every { assets.findByContent(spaceId, content.sha256) } returns existing

        assertThat(service.upload(context(ownerAccess), space.slug, UploadedFile("again.png", null, png))).isEqualTo(existing)
    }

    @Test
    fun `bytes that are not a supported image are refused`() {
        every { verifier.verify(png) } returns null

        assertThatThrownBy { service.upload(context(ownerAccess), space.slug, UploadedFile("notes.pdf", "application/pdf", png)) }
            .isInstanceOf(InvalidAssetException::class.java)
            .hasMessage("only PNG, JPEG, GIF, WebP, and SVG images can be uploaded")
    }

    @Test
    fun `an imported image keeps the address it came from`() {
        val url = "https://example.com/diagrams/flow.png?size=large"
        every { remote.fetch(url) } returns RemoteImage(url, "image/png", png)
        every { verifier.verify(png) } returns VerifiedImage(AssetMediaType.PNG, png)
        every { assets.findByContent(spaceId, content.sha256) } returns null
        every { store.put(content.sha256, "image/png", png) } just runs
        every { assets.add(any()) } answers { firstArg() }

        val asset = service.import(context(ownerAccess), space.slug, url)

        assertThat(asset.origin.fileName).isEqualTo("flow.png")
        assertThat(asset.origin.sourceUrl).isEqualTo(url)
    }

    @Test
    fun `an address that is not a web address or cannot be downloaded is refused`() {
        every { remote.fetch("https://10.0.0.1/a.png") } returns null

        assertThatThrownBy { service.import(context(ownerAccess), space.slug, "file:///etc/passwd") }
            .isInstanceOf(InvalidRequestException::class.java)
            .hasMessage("an image address must start with http:// or https://")
        assertThatThrownBy { service.import(context(ownerAccess), space.slug, "https://10.0.0.1/a.png") }
            .isInstanceOf(InvalidRequestException::class.java)
            .hasMessage("could not download a public image from https://10.0.0.1/a.png")
    }

    @Test
    fun `an asset of a space the caller cannot see is not found`() {
        val asset = Asset.upload(AssetId(UUID.randomUUID()), spaceId, content, AssetOrigin("a.png", null, owner, now))
        every { assets.findById(asset.id) } returns asset

        assertThatThrownBy {
            service.content(
                agentAccess(Permission.READ, emptySet()),
                asset.id,
            )
        }.isInstanceOf(AssetNotFoundException::class.java)
    }
}
