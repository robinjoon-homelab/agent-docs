package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentTransfer
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentTransferUseCase
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import java.security.Principal
import java.util.UUID

@RestController
class DocumentTransferController(
    private val documents: DocumentTransferUseCase,
    private val statistics: EditStatisticsRecorder,
) {
    @PostMapping("/api/documents/{id}/transfer")
    fun transfer(
        @PathVariable id: UUID,
        @RequestBody body: JsonNode,
        principal: Principal?,
    ): Map<String, Any?> {
        val base = body.get("baseVersion")?.takeIf { it.isInt }?.intValue() ?: throw InvalidRequestException("baseVersion is required")
        val request = DocumentTransfer(SpaceSlug(Wire.required(body, "space")), DocumentPath(Wire.required(body, "path")), base)
        val context = CallerAccess.write(principal, Wire.optional(body, "opId"), null)
        val written = statistics.countConflict(context.access, DocumentId(id)) { documents.transfer(context, DocumentId(id), request) }
        statistics.agent(context.access, DocumentId(id))
        return DocumentTransferJson.result(written)
    }
}
