package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import java.security.Principal
import java.util.UUID

@RestController
class FlowDesignController(
    private val actions: FlowDesignActions,
) {
    @PostMapping("/api/documents/{id}/flow-design/read")
    fun read(
        @PathVariable id: UUID,
        @RequestBody body: JsonNode,
        principal: Principal?,
    ): Map<String, Any?> = actions.read(CallerAccess.of(principal), DocumentId(id), body)

    @PostMapping("/api/documents/{id}/flow-design/patch")
    fun patch(
        @PathVariable id: UUID,
        @RequestBody body: JsonNode,
        principal: Principal?,
    ): Map<String, Any?> {
        val context = CallerAccess.write(principal, Wire.optional(body, "opId"), Wire.optional(body, "summary"))
        return actions.patch(context, DocumentId(id), body)
    }
}
