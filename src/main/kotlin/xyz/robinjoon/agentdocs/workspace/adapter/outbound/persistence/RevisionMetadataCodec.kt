package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import tools.jackson.databind.json.JsonMapper
import xyz.robinjoon.agentdocs.workspace.domain.document.history.CodeReference
import xyz.robinjoon.agentdocs.workspace.domain.document.history.ImportProvenance

internal object RevisionMetadataCodec {
    private val json = JsonMapper.builder().build()

    fun references(values: List<CodeReference>): String? =
        values.takeIf { it.isNotEmpty() }?.let {
            json.writeValueAsString(
                it.map { value ->
                    mapOf(
                        "repository" to value.repository,
                        "commit" to value.commit,
                        "pullRequest" to value.pullRequest,
                    )
                },
            )
        }

    fun readReferences(value: String?): List<CodeReference> =
        value
            ?.let {
                json.readTree(it).values().map { node ->
                    CodeReference(node.text("repository"), node.optionalText("commit"), node.fieldNode("pullRequest")?.intValue())
                }
            }.orEmpty()

    fun provenance(value: ImportProvenance?): String? =
        value?.let {
            json.writeValueAsString(mapOf("tool" to it.tool, "originalPath" to it.originalPath))
        }

    fun readProvenance(value: String?): ImportProvenance? =
        value?.let {
            val node = json.readTree(it)
            ImportProvenance(node.text("tool"), node.text("originalPath"))
        }
}
