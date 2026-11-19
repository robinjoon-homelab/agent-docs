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
import xyz.robinjoon.agentdocs.workspace.application.model.OperationKey
import xyz.robinjoon.agentdocs.workspace.application.model.OperationRecord
import xyz.robinjoon.agentdocs.workspace.application.model.RemoteImage
import xyz.robinjoon.agentdocs.workspace.application.model.UploadedFile
import xyz.robinjoon.agentdocs.workspace.application.model.VerifiedImage
import xyz.robinjoon.agentdocs.workspace.application.model.VerifiedScene
import xyz.robinjoon.agentdocs.workspace.application.port.input.AssetNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.output.AssetRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.AssetStore
import xyz.robinjoon.agentdocs.workspace.application.port.output.ImageVerifier
import xyz.robinjoon.agentdocs.workspace.application.port.output.OperationStore
import xyz.robinjoon.agentdocs.workspace.application.port.output.RemoteImageSource
import xyz.robinjoon.agentdocs.workspace.application.port.output.SceneVerifier
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceRepository
import xyz.robinjoon.agentdocs.workspace.application.service.AssetService
import xyz.robinjoon.agentdocs.workspace.application.service.AttachmentVerifier
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
    private val scenes = mockk<SceneVerifier>()
    private val remote = mockk<RemoteImageSource>()
    private val activity = mockk<ActivityReporter>()
    private val operations = mockk<OperationStore>()
    private val service =
        AssetService(
            spaces,
            assets,
            store,
            AttachmentVerifier(verifier, scenes),
            remote,
            WriteJournal(activity, operations, WorkspaceFixtures.clock),
        )
    private val png = byteArrayOf(1, 2, 3)
    private val content = AssetContent.of(AssetMediaType.PNG, png)

    init {
        every { spaces.findBySlug(space.slug) } returns space
        every { activity.record(any()) } just runs
    }

    @Test
    fun `an upload keeps the file under its content hash with the uploader and name`() {
        every { verifier.verify(png) } returns VerifiedImage(AssetMediaType.PNG, png)
        every { store.put(content.sha256, "image/png", png) } just runs
        every { assets.add(any()) } answers { firstArg() }

        val asset = service.upload(context(ownerAccess), space.slug, UploadedFile("C:\\shots\\flow.png", "image/png", png)).asset

        assertThat(asset.content).isEqualTo(content)
        assertThat(asset.origin).isEqualTo(AssetOrigin("flow.png", null, owner, now))
    }

    @Test
    fun `a drawing scene is kept byte for byte as an Excalidraw attachment`() {
        val scene = """{"type":"excalidraw","elements":[]}""".toByteArray()
        val stored = AssetContent.of(AssetMediaType.EXCALIDRAW, scene)
        every { verifier.verify(scene) } returns null
        every { scenes.verify(scene) } returns VerifiedScene(0, 0, emptyList())
        every { store.put(stored.sha256, "application/vnd.excalidraw+json", scene) } just runs
        every { assets.add(any()) } answers { firstArg() }

        val asset =
            service
                .upload(
                    context(ownerAccess),
                    space.slug,
                    UploadedFile("draw0001.excalidraw", "application/octet-stream", scene),
                ).asset

        assertThat(asset.content).isEqualTo(stored)
    }

    @Test
    fun `the same bytes imported from another address are a new asset that keeps that address`() {
        val url = "https://mirror.example.com/flow.png"
        every { remote.fetch(url) } returns RemoteImage(url, "image/png", png)
        every { verifier.verify(png) } returns VerifiedImage(AssetMediaType.PNG, png)
        every { store.put(content.sha256, "image/png", png) } just runs
        every { assets.add(any()) } answers { firstArg() }

        val first = service.upload(context(ownerAccess), space.slug, UploadedFile("flow.png", "image/png", png)).asset
        val again = service.import(context(ownerAccess), space.slug, url).asset

        assertThat(again.id).isNotEqualTo(first.id)
        assertThat(again.content).isEqualTo(first.content)
        assertThat(again.origin.sourceUrl).isEqualTo(url)
    }

    @Test
    fun `a claimed PDF without its signature is rejected`() {
        every { verifier.verify(png) } returns null

        assertThatThrownBy { service.upload(context(ownerAccess), space.slug, UploadedFile("notes.pdf", "application/pdf", png)) }
            .isInstanceOf(InvalidAssetException::class.java)
            .hasMessage("PDF content must start with %PDF-")
    }

    @Test
    fun `a file name that cannot be kept leaves no bytes behind, because storage has no rollback`() {
        val kept = KeptFiles()
        val keeping =
            AssetService(
                spaces,
                assets,
                kept,
                AttachmentVerifier(verifier, scenes),
                remote,
                WriteJournal(activity, operations, WorkspaceFixtures.clock),
            )
        every { verifier.verify(png) } returns VerifiedImage(AssetMediaType.PNG, png)

        assertThatThrownBy { keeping.upload(context(ownerAccess), space.slug, UploadedFile("a/b.png", "image/png", png)) }
            .isInstanceOf(InvalidAssetException::class.java)

        assertThat(kept.hashes).isEmpty()
    }

    @Test
    fun `an imported image keeps the address it came from`() {
        val url = "https://example.com/diagrams/flow.png?size=large"
        every { remote.fetch(url) } returns RemoteImage(url, "image/png", png)
        every { verifier.verify(png) } returns VerifiedImage(AssetMediaType.PNG, png)
        every { store.put(content.sha256, "image/png", png) } just runs
        every { assets.add(any()) } answers { firstArg() }

        val asset = service.import(context(ownerAccess), space.slug, url).asset

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
    fun `direct uploads with one key keep the original ID and Markdown label`() {
        val key = OperationKey(owner.userId.toString(), "upload-1")
        val context = context(ownerAccess, operationId = "upload-1")
        var stored: OperationRecord? = null
        every { operations.findForWrite(key) } answers { stored }
        every { operations.save(key, any(), now) } answers { stored = secondArg() }
        every { verifier.verify(png) } returns VerifiedImage(AssetMediaType.PNG, png)
        every { store.put(content.sha256, "image/png", png) } just runs
        every { assets.add(any()) } answers { firstArg() }

        val first = service.upload(context, space.slug, UploadedFile("first.png", "image/png", png), "원본")
        every { assets.findByIdForUpdate(first.asset.id) } returns first.asset
        val replay = service.upload(context, space.slug, UploadedFile("different.png", "image/png", png), "다른 설명")

        assertThat(replay).isEqualTo(first)
        assertThat(replay.alt).isEqualTo("원본")
    }

    @Test
    fun `a retried URL import returns its first attachment and label from its current space`() {
        val currentSpace = WorkspaceFixtures.otherSpaceId
        val access = agentAccess(Permission.EDIT, setOf(currentSpace))
        val context = context(access, operationId = "upload-1")
        val asset = Asset.upload(AssetId(UUID.randomUUID()), currentSpace, content, AssetOrigin("flow.png", null, owner, now))
        val record = OperationRecord("asset.import", asset.id.value, null, assetAlt = "first label")
        every { operations.findForWrite(OperationKey(WorkspaceFixtures.connectionId.toString(), "upload-1")) } returns record
        every { assets.findByIdForUpdate(asset.id) } returns asset

        val replayed = service.import(context, space.slug, "https://example.com/changed.png", "changed label")

        assertThat(replayed.asset).isEqualTo(asset)
        assertThat(replayed.alt).isEqualTo("first label")
        assertThatThrownBy {
            service.import(context.copy(access = agentAccess(Permission.EDIT, setOf(spaceId))), space.slug, "https://example.com/flow.png")
        }.isInstanceOf(AssetNotFoundException::class.java)
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

private class KeptFiles : AssetStore {
    val hashes = mutableSetOf<String>()

    override fun put(
        sha256: String,
        mediaType: String,
        bytes: ByteArray,
    ) {
        hashes += sha256
    }

    override fun get(sha256: String): ByteArray = error("not read here")
}
