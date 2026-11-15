package xyz.robinjoon.agentdocs.workspace.adapter.outbound.archive

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import xyz.robinjoon.agentdocs.workspace.application.model.ExportAsset
import xyz.robinjoon.agentdocs.workspace.application.model.WorkspaceExport
import xyz.robinjoon.agentdocs.workspace.application.port.output.AssetStore
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class ZipExportArchiveTest {
    @Test
    fun `each moved document reads its stored original while canonical asset references remain unchanged`() {
        val store = mockk<AssetStore>()
        every { store.get("first-hash") } returns byteArrayOf(1)
        every { store.get("second-hash") } returns byteArrayOf(2)
        val bindings =
            listOf(
                mapOf("document_id" to "first", "referenced_asset_id" to "old", "stored_asset_id" to "first-copy"),
                mapOf("document_id" to "second", "referenced_asset_id" to "old", "stored_asset_id" to "second-copy"),
            )
        val snapshot =
            WorkspaceExport(
                mapOf("documentAssetBindings" to bindings, "documents" to listOf(mapOf("body" to "![file](asset:old)"))),
                mapOf("first" to "![file](asset:old)", "second" to "![file](asset:old)"),
                listOf(ExportAsset("first-copy", "png", "first-hash"), ExportAsset("second-copy", "png", "second-hash")),
            )
        val bytes = ZipExportArchive(JsonMapper.builder().build(), store).zip(snapshot)
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry -> entries[entry.name] = zip.readBytes() }
        }
        assertThat(requireNotNull(entries["markdown/first.md"]).toString(Charsets.UTF_8)).isEqualTo("![file](../assets/first-copy.png)")
        assertThat(requireNotNull(entries["markdown/second.md"]).toString(Charsets.UTF_8)).isEqualTo("![file](../assets/second-copy.png)")
        assertThat(entries["assets/first-copy.png"]).containsExactly(1)
        assertThat(entries["assets/second-copy.png"]).containsExactly(2)
        assertThat(requireNotNull(entries["canonical.json"]).toString(Charsets.UTF_8)).contains("asset:old", "documentAssetBindings")
    }

    @Test
    fun `one archive preserves canonical pending state markdown and original attachment bytes`() {
        val store = mockk<AssetStore>()
        every { store.get("hash") } returns byteArrayOf(1, 2, 3)
        val snapshot =
            WorkspaceExport(
                mapOf("revisions" to listOf(mapOf("pending" to true, "content_version" to 8))),
                mapOf("doc" to "# title"),
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
