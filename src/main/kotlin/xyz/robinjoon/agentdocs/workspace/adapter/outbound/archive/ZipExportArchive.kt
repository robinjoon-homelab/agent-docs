package xyz.robinjoon.agentdocs.workspace.adapter.outbound.archive

import tools.jackson.databind.ObjectMapper
import xyz.robinjoon.agentdocs.workspace.application.model.AssetContentMissingException
import xyz.robinjoon.agentdocs.workspace.application.model.ExportAssetMissingException
import xyz.robinjoon.agentdocs.workspace.application.model.ExportDocument
import xyz.robinjoon.agentdocs.workspace.application.model.WorkspaceExport
import xyz.robinjoon.agentdocs.workspace.application.port.output.AssetStore
import xyz.robinjoon.agentdocs.workspace.application.port.output.ExportArchive
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowDesignBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipExportArchive(
    private val mapper: ObjectMapper,
    private val store: AssetStore,
) : ExportArchive {
    override fun zip(snapshot: WorkspaceExport): ByteArray {
        val buffer = ByteArrayOutputStream()
        val paths = snapshot.assets.associate { it.id to "../assets/${it.id}.${it.extension}" }
        val bindings = snapshot.records["documentAssetBindings"].orEmpty().groupBy { it["document_id"] }
        ZipOutputStream(buffer, Charsets.UTF_8).use { zip ->
            entry(zip, "canonical.json", mapper.writeValueAsBytes(mapOf("formatVersion" to 1, "data" to snapshot.records)))
            snapshot.documents.forEach { (id, document) ->
                val markdown = readable(document, paths, bindings[id].orEmpty())
                entry(zip, "markdown/$id.md", markdown.toByteArray(Charsets.UTF_8))
            }
            snapshot.assets.forEach { asset ->
                val bytes =
                    try {
                        store.get(asset.sha256)
                    } catch (missing: AssetContentMissingException) {
                        throw ExportAssetMissingException(asset, missing)
                    }
                entry(zip, "assets/${asset.id}.${asset.extension}", bytes)
            }
        }
        return buffer.toByteArray()
    }

    private fun readable(
        document: ExportDocument,
        paths: Map<String, String>,
        bindings: List<Map<String, Any?>>,
    ): String {
        if (document.blocks.firstOrNull() is FlowDesignBlock) {
            return "---\ntype: flow\n---\n\n" + BlockMarkdown.write(document.blocks, annotated = true)
        }
        val boundPaths =
            bindings.associate {
                it.getValue("referenced_asset_id") to
                    paths.getValue(it.getValue("stored_asset_id").toString())
            }
        val body = BlockMarkdown.forExport(document.blocks) { asset -> boundPaths[asset.toString()] ?: paths[asset.toString()] }
        if (document.blocks.firstOrNull() is xyz.robinjoon.agentdocs.workspace.domain.document.FlowSettings) {
            return "---\ntype: flow\n---\n\n$body"
        }
        return "# ${document.title}\n\n$body"
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
