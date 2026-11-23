package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentUseCase
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import java.security.Principal
import java.util.UUID

@RestController
class FlowUpgradeController(
    private val documents: DocumentUseCase,
    private val recorder: EditStatisticsRecorder,
) {
    @PostMapping("/api/documents/{id}/flow-design/upgrade")
    fun upgrade(
        @PathVariable id: UUID,
        @RequestBody body: JsonNode,
        principal: Principal?,
    ): Map<String, Any?> {
        Wire.requireObject(body)
        val version =
            body.get("baseVersion")?.takeIf(JsonNode::isInt)?.intValue() ?: throw InvalidRequestException("baseVersion must be an integer")
        val context = CallerAccess.write(principal, Wire.optional(body, "opId"), Wire.optional(body, "summary"))
        val documentId = DocumentId(id)
        val result =
            recorder.countConflict(context.access, documentId) {
                documents.upgradeFlow(RevisionMetadataJson.context(context, body), documentId, version)
            }
        if (context.access.actor.isAgent) recorder.agent(context.access, documentId)
        return WorkspaceJson.writeResult(result)
    }
}
