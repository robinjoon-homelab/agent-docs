package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import tools.jackson.databind.JsonNode
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentView
import xyz.robinjoon.agentdocs.workspace.application.model.PatchedDesignTarget
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentQueryUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentUseCase
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.EditOperation
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown

class FlowDesignActions(
    private val queries: DocumentQueryUseCase,
    private val documents: DocumentUseCase,
    private val recorder: EditStatisticsRecorder,
    private val publicUrl: String,
) {
    fun read(
        access: Access,
        id: DocumentId,
        body: JsonNode,
    ): Map<String, Any?> = browse(queries.open(access, id), DesignRequest(body)).page()

    fun patch(
        context: WriteContext,
        id: DocumentId,
        body: JsonNode,
    ): Map<String, Any?> {
        val request = DesignRequest(body)
        val patch = request.patch()
        val written =
            recorder.countConflict(context.access, id) {
                documents.edit(context, id, listOf(EditOperation.DesignField(patch)), null)
            }
        if (context.access.actor.isAgent) recorder.agent(context.access, written.document.id)
        val view = queries.open(context.access, written.document.id)
        val target = written.designPatch ?: PatchedDesignTarget(patch.screen, patch.state, patch.target, patch.field.vertex)
        return mapOf(
            "document" to written.document.id.value,
            "revision" to written.revision,
            "version" to written.document.version,
            "unchanged" to written.unchanged,
            "warnings" to WorkspaceJson.warnings(written),
            "block" to target.state.value,
            "hash" to
                written.document.blocks
                    .find { it.id == target.state }
                    ?.let(BlockMarkdown::hash),
            "url" to browse(view, request).link(target.screen, target.state, target.address),
        ) + (target.vertex?.let { mapOf("parts" to it.parts()) } ?: emptyMap())
    }

    private fun browse(
        view: DocumentView,
        request: DesignRequest,
    ): DesignBrowse = DesignBrowse(view, request, "${publicUrl.trimEnd('/')}/s/${view.space.slug.value}/d/${view.document.id}")
}
