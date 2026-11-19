package xyz.robinjoon.agentdocs.workspace.adapter.outbound.archive

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tools.jackson.databind.json.JsonMapper
import xyz.robinjoon.agentdocs.workspace.application.model.AssetContentMissingException
import xyz.robinjoon.agentdocs.workspace.application.model.ExportAsset
import xyz.robinjoon.agentdocs.workspace.application.model.ExportAssetMissingException
import xyz.robinjoon.agentdocs.workspace.application.model.ExportDocument
import xyz.robinjoon.agentdocs.workspace.application.model.WorkspaceExport
import xyz.robinjoon.agentdocs.workspace.application.port.output.AssetStore
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.CodeBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.ImageBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.InlineText
import xyz.robinjoon.agentdocs.workspace.domain.document.Paragraph
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.zip.ZipInputStream

class ZipExportArchiveTest {
    @Test
    fun `only attachment destinations are rewritten in reading markdown`() {
        val asset = AssetId(UUID.fromString("12345678-abcd-4321-8765-567890abcdef"))
        val uri = "asset:$asset"
        val path = "../assets/$asset.png"
        val examples = "그대로 $uri, ``[코드]($uri)``, \\[예시]($uri)"
        val label = "첨부 [$uri]"
        val caption = "설명 \"$uri\""
        val blocks =
            listOf(
                Paragraph(BlockId("para01"), InlineText("$examples, [$label](<$uri>)")),
                CodeBlock(BlockId("code01"), "markdown", "![예시]($uri)"),
                ImageBlock(BlockId("img001"), asset, "그림] $uri", caption),
            )
        val snapshot =
            WorkspaceExport(
                emptyMap(),
                mapOf("doc" to ExportDocument("제목 $uri", blocks)),
                listOf(ExportAsset(asset.toString(), "png", "hash")),
            )
        val store = mockk<AssetStore>()
        every { store.get("hash") } returns byteArrayOf(1)
        val bytes = ZipExportArchive(JsonMapper.builder().build(), store).zip(snapshot)

        val markdown =
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                generateSequence { zip.nextEntry }.first { it.name == "markdown/doc.md" }
                zip.readBytes().toString(Charsets.UTF_8)
            }
        assertThat(markdown).isEqualTo(
            "# 제목 $uri\n\n$examples, [$label](<$path>)\n\n```markdown\n![예시]($uri)\n```\n\n" +
                "![그림\\] $uri]($path \"설명 \\\"$uri\\\"\")",
        )
    }

    @Test
    fun `a missing original fails the archive with the first affected asset and its cause`() {
        val store = mockk<AssetStore>()
        val missing = AssetContentMissingException("missing-hash")
        val failed = ExportAsset("missing-asset", "html", "missing-hash")
        every { store.get("available-hash") } returns byteArrayOf(1)
        every { store.get("missing-hash") } throws missing
        val snapshot =
            WorkspaceExport(
                emptyMap(),
                emptyMap(),
                listOf(ExportAsset("available-asset", "html", "available-hash"), failed, ExportAsset("unchecked", "png", "other-hash")),
            )

        val failure = assertThrows<ExportAssetMissingException> { ZipExportArchive(JsonMapper.builder().build(), store).zip(snapshot) }

        assertThat(failure.asset).isEqualTo(failed)
        assertThat(failure.cause).isSameAs(missing)
    }

    @Test
    fun `a storage failure other than missing content keeps its original failure`() {
        val store = mockk<AssetStore>()
        val failure = IllegalStateException("storage unavailable")
        every { store.get("hash") } throws failure
        val snapshot = WorkspaceExport(emptyMap(), emptyMap(), listOf(ExportAsset("asset", "html", "hash")))

        assertThat(assertThrows<IllegalStateException> { ZipExportArchive(JsonMapper.builder().build(), store).zip(snapshot) })
            .isSameAs(failure)
    }

    @Test
    fun `different asset ids sharing a hash retain both original entries`() {
        val store = mockk<AssetStore>()
        every { store.get("shared-hash") } returns byteArrayOf(1, 2)
        val snapshot =
            WorkspaceExport(
                emptyMap(),
                emptyMap(),
                listOf(ExportAsset("first", "html", "shared-hash"), ExportAsset("second", "html", "shared-hash")),
            )
        val bytes = ZipExportArchive(JsonMapper.builder().build(), store).zip(snapshot)
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry -> entries[entry.name] = zip.readBytes() }
        }
        assertThat(entries["assets/first.html"]).containsExactly(1, 2)
        assertThat(entries["assets/second.html"]).containsExactly(1, 2)
    }

    @Test
    fun `each moved document reads its stored original while canonical asset references remain unchanged`() {
        val store = mockk<AssetStore>()
        every { store.get("first-hash") } returns byteArrayOf(1)
        every { store.get("second-hash") } returns byteArrayOf(2)
        val old = AssetId(UUID.randomUUID())
        val document = ExportDocument("title", listOf(ImageBlock(BlockId("file"), old, "file", "")))
        val bindings =
            listOf(
                mapOf("document_id" to "first", "referenced_asset_id" to old.toString(), "stored_asset_id" to "first-copy"),
                mapOf("document_id" to "second", "referenced_asset_id" to old.toString(), "stored_asset_id" to "second-copy"),
            )
        val snapshot =
            WorkspaceExport(
                mapOf(
                    "documentAssetBindings" to bindings,
                    "documents" to listOf(mapOf("body" to BlockMarkdown.write(document.blocks, false))),
                ),
                mapOf("first" to document, "second" to document),
                listOf(ExportAsset("first-copy", "png", "first-hash"), ExportAsset("second-copy", "png", "second-hash")),
            )
        val bytes = ZipExportArchive(JsonMapper.builder().build(), store).zip(snapshot)
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry -> entries[entry.name] = zip.readBytes() }
        }
        assertThat(
            requireNotNull(entries["markdown/first.md"]).toString(Charsets.UTF_8),
        ).isEqualTo("# title\n\n![file](../assets/first-copy.png)")
        assertThat(
            requireNotNull(entries["markdown/second.md"]).toString(Charsets.UTF_8),
        ).isEqualTo("# title\n\n![file](../assets/second-copy.png)")
        assertThat(entries["assets/first-copy.png"]).containsExactly(1)
        assertThat(entries["assets/second-copy.png"]).containsExactly(2)
        assertThat(requireNotNull(entries["canonical.json"]).toString(Charsets.UTF_8)).contains("asset:$old", "documentAssetBindings")
    }

    @Test
    fun `one archive preserves canonical pending state markdown and original attachment bytes`() {
        val store = mockk<AssetStore>()
        every { store.get("hash") } returns byteArrayOf(1, 2, 3)
        val snapshot =
            WorkspaceExport(
                mapOf("revisions" to listOf(mapOf("pending" to true, "content_version" to 8))),
                mapOf("doc" to ExportDocument("title", emptyList())),
                listOf(ExportAsset("asset", "png", "hash")),
            )
        val bytes = ZipExportArchive(JsonMapper.builder().build(), store).zip(snapshot)
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry -> entries[entry.name] = zip.readBytes() }
        }
        assertThat(entries.keys).containsExactly("canonical.json", "markdown/doc.md", "assets/asset.png")
        assertThat(entries["assets/asset.png"]).containsExactly(1, 2, 3)
        assertThat(
            requireNotNull(entries["canonical.json"]).toString(Charsets.UTF_8),
        ).contains("\"formatVersion\":1", "\"pending\":true", "\"content_version\":8")
    }
}
