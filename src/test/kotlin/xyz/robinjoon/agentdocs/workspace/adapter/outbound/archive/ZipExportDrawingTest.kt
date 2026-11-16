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

class ZipExportDrawingTest {
    @Test
    fun `a moved drawing's readable markdown points at both stored files and both originals are in the archive`() {
        val store = mockk<AssetStore>()
        every { store.get("svg-hash") } returns "<svg/>".toByteArray()
        every { store.get("scene-hash") } returns """{"type":"excalidraw"}""".toByteArray()
        val markdown = "![흐름](asset:old-svg \"excalidraw asset:old-scene\")"
        val bindings =
            listOf(
                mapOf("document_id" to "moved", "referenced_asset_id" to "old-svg", "stored_asset_id" to "svg-copy"),
                mapOf("document_id" to "moved", "referenced_asset_id" to "old-scene", "stored_asset_id" to "scene-copy"),
            )
        val snapshot =
            WorkspaceExport(
                mapOf("documentAssetBindings" to bindings),
                mapOf("moved" to markdown),
                listOf(ExportAsset("svg-copy", "svg", "svg-hash"), ExportAsset("scene-copy", "excalidraw", "scene-hash")),
            )

        val bytes = ZipExportArchive(JsonMapper.builder().build(), store).zip(snapshot)
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry -> entries[entry.name] = zip.readBytes() }
        }

        assertThat(requireNotNull(entries["markdown/moved.md"]).toString(Charsets.UTF_8))
            .isEqualTo("![흐름](../assets/svg-copy.svg \"excalidraw ../assets/scene-copy.excalidraw\")")
        assertThat(requireNotNull(entries["assets/scene-copy.excalidraw"]).toString(Charsets.UTF_8)).isEqualTo("""{"type":"excalidraw"}""")
        assertThat(requireNotNull(entries["assets/svg-copy.svg"]).toString(Charsets.UTF_8)).isEqualTo("<svg/>")
    }
}
