package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import tools.jackson.databind.JsonNode
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.designmarkup.AuthorTarget
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.designmarkup.DesignMarkup
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.designmarkup.MarkupInsertion
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.designmarkup.invalid
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentWrite
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentQueryUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.StaleRevisionException
import xyz.robinjoon.agentdocs.workspace.domain.document.DesignJsonReader
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowDesignBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.design.DesignAddress
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown

class FlowDesignAuthorActions(
    private val queries: DocumentQueryUseCase,
    private val documents: DocumentUseCase,
    private val recorder: EditStatisticsRecorder,
    private val publicUrl: String,
) {
    fun author(
        context: WriteContext,
        id: DocumentId,
        body: JsonNode,
    ): Map<String, Any?> {
        val version =
            body.get("baseVersion")?.takeIf { it.isIntegralNumber && it.canConvertToInt() }?.intValue()
                ?: invalid("An integer baseVersion is required; read the document first")
        val request = DesignRequest(body)
        val target = target(request, body)
        val source = request.requiredString("markup")
        if (source.length !in 1..MAX_MARKUP_CHARS) invalid("Expected 1..$MAX_MARKUP_CHARS markup characters")
        val unknown = body.propertyNames() - keys
        if (unknown.isNotEmpty()) invalid("Unknown authoring fields: " + unknown.joinToString())
        val view = queries.open(context.access, id)
        val doc = view.document
        // An opId retry reaches the journal before a stale version is rejected under the document lock.
        val blocks = if (doc.version == version) DesignMarkup(doc.blocks).create(source, target) else doc.blocks
        val result =
            try {
                recorder.countConflict(context.access, id) { documents.save(context, id, version, blocks) }
            } catch (error: StaleRevisionException) {
                throw DesignAuthorConflict(error.document?.version, error)
            }
        if (context.access.actor.isAgent) recorder.agent(context.access, result.document.id)
        return response(result, target.parent, "${publicUrl.trimEnd('/')}/s/${view.space.slug.value}/d/$id")
    }

    private fun target(
        request: DesignRequest,
        body: JsonNode,
    ): AuthorTarget =
        AuthorTarget(
            request.requiredString("kind"),
            request.optionalString("name").orEmpty().trim(),
            request.optionalString("designId"),
            props(body),
            body.get("parent")?.let { DesignAddress.from(DesignJsonReader(it.toString()).read()) },
        )

    private fun response(
        result: DocumentWrite,
        parent: DesignAddress?,
        documentUrl: String,
    ): Map<String, Any?> {
        val affected = result.changedBlocks.toSet()
        val changes =
            result.document.blocks.filterIsInstance<FlowDesignBlock>().filter { it.id in affected }.map {
                val node = parent?.takeIf { p -> p.owner == it.designId }?.let { p -> MarkupInsertion.lastChild(it, p) }
                val block = mapOf("block" to it.id.value, "designId" to it.designId, "kind" to it.kind, "hash" to BlockMarkdown.hash(it))
                val link = if (it.kind == "screen") mapOf("url" to "$documentUrl?flowScreen=${it.id}") else emptyMap()
                if (node == null) block + link else block + link + ("addedNode" to node)
            }
        return mapOf(
            "document" to result.document.id.value,
            "version" to result.document.version,
            "revision" to result.revision,
            "changes" to changes,
            "warnings" to WorkspaceJson.warnings(result),
        )
    }

    private fun props(body: JsonNode): List<String> {
        val raw = body.get("props") ?: return emptyList()
        if (!raw.isArray) invalid("props must be an array of text property names")
        return raw.values().map { it.takeIf(JsonNode::isString)?.stringValue() ?: invalid("Expected a text property name") }
    }

    private companion object {
        val keys = setOf("document", "baseVersion", "kind", "name", "designId", "props", "parent", "markup", "opId", "summary")
        const val MAX_MARKUP_CHARS = 200_000
    }
}

class DesignAuthorConflict(
    val version: Int?,
    cause: StaleRevisionException,
) : RuntimeException("The design document changed", cause) {
    val fix = "Read the target again and retry with its current baseVersion"
}
