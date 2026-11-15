package xyz.robinjoon.agentdocs.workspace.adapter.outbound.archive

import tools.jackson.databind.ObjectMapper
import xyz.robinjoon.agentdocs.workspace.application.model.WorkspaceExport
import xyz.robinjoon.agentdocs.workspace.application.port.output.AssetStore
import xyz.robinjoon.agentdocs.workspace.application.port.output.ExportArchive
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipExportArchive(
    private val mapper: ObjectMapper,
    private val store: AssetStore,
) : ExportArchive {
    override fun zip(snapshot: WorkspaceExport): ByteArray {
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer, Charsets.UTF_8).use { zip ->
            entry(zip, "canonical.json", mapper.writeValueAsBytes(mapOf("formatVersion" to 1, "data" to snapshot.records)))
            snapshot.markdown.forEach { (id, markdown) ->
                entry(zip, "markdown/$id.md", readable(id, markdown, snapshot).toByteArray(Charsets.UTF_8))
            }
            snapshot.assets.forEach { asset -> entry(zip, "assets/${asset.id}.${asset.extension}", store.get(asset.sha256)) }
        }
        return buffer.toByteArray()
    }

    private fun readable(
        documentId: String,
        markdown: String,
        snapshot: WorkspaceExport,
    ): String {
        val bindings = snapshot.records["documentAssetBindings"].orEmpty().filter { it["document_id"] == documentId }
        val resolved =
            bindings.fold(markdown) { text, binding ->
                val stored = requireNotNull(snapshot.assets.find { it.id == binding["stored_asset_id"] })
                text.replace("asset:${binding["referenced_asset_id"]}", "../assets/${stored.id}.${stored.extension}")
            }
        return snapshot.assets.fold(resolved) { text, asset ->
            text.replace("asset:${asset.id}", "../assets/${asset.id}.${asset.extension}")
        }
    }

    private fun entry(
        zip: ZipOutputStream,
        path: String,
        bytes: ByteArray,
    ) {
        require(path.split('/').none { it == ".." } && !path.startsWith('/'))
        zip.putNextEntry(ZipEntry(path))
        zip.write(bytes)
        zip.closeEntry()
    }
}
