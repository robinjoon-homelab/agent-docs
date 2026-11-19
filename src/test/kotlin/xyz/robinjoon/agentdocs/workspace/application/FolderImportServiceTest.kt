package xyz.robinjoon.agentdocs.workspace.application

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentDraft
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentWrite
import xyz.robinjoon.agentdocs.workspace.application.model.FolderFile
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.application.port.input.AssetUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentUseCase
import xyz.robinjoon.agentdocs.workspace.application.service.FolderImportService
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.Actor
import xyz.robinjoon.agentdocs.workspace.domain.Permission
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.SpaceScope
import xyz.robinjoon.agentdocs.workspace.domain.asset.Asset
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetContent
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetMediaType
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetOrigin
import xyz.robinjoon.agentdocs.workspace.domain.document.ChangeSource
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentHeader
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentProperties
import xyz.robinjoon.agentdocs.workspace.domain.document.ImageBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.PathTakenException
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import java.time.Instant
import java.util.UUID

class FolderImportServiceTest {
    private val assets = mockk<AssetUseCase>()
    private val documents = mockk<DocumentUseCase>()
    private val service = FolderImportService(assets, documents)
    private val context =
        WriteContext(
            Access(Actor.human(UUID.randomUUID(), "owner"), SpaceScope.AllSpaces, Permission.OWNER),
            ChangeSource.REST,
            null,
            null,
        )

    @Test
    fun `a storage failure reports a per file failure without exposing the IO cause`() {
        every { assets.upload(any(), SpaceSlug("docs"), any()) } throws
            xyz.robinjoon.agentdocs.workspace.application.model
                .AssetStorageException(java.io.IOException("private path"))
        val result =
            service.importFolder(
                context,
                SpaceSlug("docs"),
                "Markdown",
                listOf(file("doc.md", "![image](image.png)"), FolderFile("image.png", "image/png", byteArrayOf(1))),
            )
        assertThat(result.files.first().status).isEqualTo("failed")
        assertThat(result.files.first().code).isEqualTo("file_io_failed")
        assertThat(result.files.first().relatedPath).isNull()
    }

    @Test
    fun `missing images fail their document and do not prevent another document succeeding`() {
        val document =
            Document.create(
                DocumentId(UUID.randomUUID()),
                DocumentHeader(SpaceId(UUID.randomUUID()), DocumentPath("valid"), "valid", DocumentProperties.initial),
                emptyList(),
                Instant.EPOCH,
            )
        every { documents.create(any(), SpaceSlug("docs"), any()) } returns DocumentWrite(document, 1, emptyList(), false, false)
        val result =
            service.importFolder(
                context,
                SpaceSlug("docs"),
                "Obsidian",
                listOf(
                    file("broken.md", "![missing](missing.png)"),
                    file("valid.md", "hello"),
                ),
            )
        assertThat(result.files.map { it.status }).containsExactly("failed", "created")
        assertThat(result.files.first().code).isEqualTo("missing_image")
        assertThat(result.files.first().relatedPath).isEqualTo("missing.png")
        assertThat(result.files.last().documentId).isEqualTo(document.id.value.toString())
    }

    @Test
    fun `duplicate unsafe and occupied paths return explicit failures`() {
        every { documents.create(any(), SpaceSlug("docs"), any()) } throws PathTakenException(DocumentPath("occupied"))
        val result =
            service.importFolder(
                context,
                SpaceSlug("docs"),
                "Markdown",
                listOf(
                    file("duplicate.md", "one"),
                    file("duplicate.md", "two"),
                    file("/private/secret.md", "secret"),
                    file("occupied.md", "three"),
                ),
            )
        assertThat(result.files.map { it.code }).containsExactly("duplicate_path", "duplicate_path", "unsafe_path", "path_taken")
        assertThat(result.files.map { it.path }).doesNotContain("/private/secret.md")
    }

    @Test
    fun `re-importing exported reading markdown turns a drawing with escaped brackets into an ordinary SVG image`() {
        val uploadedSvg =
            Asset.upload(
                AssetId(UUID.randomUUID()),
                SpaceId(UUID.randomUUID()),
                AssetContent.of(AssetMediaType.SVG, "<svg/>".toByteArray()),
                AssetOrigin("svg-1.svg", null, WorkspaceFixtures.owner, WorkspaceFixtures.now),
            )
        val created =
            Document.create(
                DocumentId(UUID.randomUUID()),
                DocumentHeader(SpaceId(UUID.randomUUID()), DocumentPath("markdown/flow"), "flow", DocumentProperties.initial),
                emptyList(),
                Instant.EPOCH,
            )
        every { assets.upload(any(), SpaceSlug("docs"), any()) } returns uploadedSvg
        every {
            documents.create(
                any(),
                SpaceSlug("docs"),
                match {
                    val block = it.blocks.singleOrNull() as? ImageBlock
                    block != null && block.asset == uploadedSvg.id && block.alt == "배포 흐름 [초안]"
                },
            )
        } returns DocumentWrite(created, 1, emptyList(), false, false)

        val result =
            service.importFolder(
                context,
                SpaceSlug("docs"),
                "Markdown",
                listOf(
                    file("markdown/flow.md", """![배포 흐름 [초안\]](../assets/svg-1.svg "excalidraw ../assets/scene-1.excalidraw")"""),
                    FolderFile("assets/svg-1.svg", "image/svg+xml", "<svg/>".toByteArray()),
                    FolderFile(
                        "assets/scene-1.excalidraw",
                        "application/vnd.excalidraw+json",
                        """{"type":"excalidraw","elements":[]}""".toByteArray(),
                    ),
                ),
            )

        assertThat(result.files.map { it.status }).containsExactly("created", "skipped", "skipped")
        assertThat(result.files.first().documentId).isEqualTo(created.id.value.toString())
    }

    private fun file(
        path: String,
        body: String,
    ): FolderFile = FolderFile(path, "text/markdown", body.toByteArray())
}
