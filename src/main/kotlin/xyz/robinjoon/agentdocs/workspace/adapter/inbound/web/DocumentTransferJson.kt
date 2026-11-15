package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import xyz.robinjoon.agentdocs.workspace.application.model.DocumentWrite
import xyz.robinjoon.agentdocs.workspace.application.model.RelatedWrite

object DocumentTransferJson {
    fun result(written: DocumentWrite): Map<String, Any?> {
        val space = checkNotNull(written.space) { "a transfer result must capture its destination" }
        return mapOf(
            "documentId" to written.document.id.value,
            "space" to mapOf("id" to space.id.value, "slug" to space.slug.value, "name" to space.name),
            "path" to written.document.header.path.value,
            "version" to written.document.version,
            "revision" to written.revision,
            "unchanged" to written.unchanged,
            "alsoChanged" to written.related.map(::related),
        )
    }

    private fun related(value: RelatedWrite): Map<String, Any?> =
        mapOf(
            "documentId" to value.document.id.value,
            "path" to value.document.header.path.value,
            "title" to value.document.header.title,
            "version" to value.document.version,
            "revision" to value.document.revision,
            "change" to value.change.name.lowercase(),
        )
}
