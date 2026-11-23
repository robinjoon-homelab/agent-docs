package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.RenderSelection
import xyz.robinjoon.agentdocs.workspace.application.port.input.FlowDesignRenderUseCase
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import java.security.Principal
import java.util.UUID

internal fun renderSelection(body: JsonNode): RenderSelection {
    val request = DesignRequest(body)
    val allowed = setOf("document", "screen", "state", "target", "mode", "image")
    if ((body.propertyNames() - allowed).isNotEmpty()) throw InvalidRequestException("Unknown render request field")
    val image = body.get("image")?.let { if (it.isBoolean) it.booleanValue() else throw InvalidRequestException("image must be boolean") }
    val target = if (body.has("target")) request.target() else request.selected
    return RenderSelection(
        request.screen ?: throw InvalidRequestException("screen is required"),
        request.state,
        target,
        request.optionalString("mode") ?: "viewport",
        image ?: false,
    )
}

@RestController
class FlowDesignRenderController(
    private val renders: FlowDesignRenderUseCase,
) {
    @PostMapping("/api/documents/{id}/flow-design/render")
    fun render(
        @PathVariable id: UUID,
        @RequestBody body: JsonNode,
        principal: Principal?,
    ): Map<String, Any?> {
        val result = renders.render(CallerAccess.of(principal), DocumentId(id), renderSelection(body))
        val image = result.png ?: return result.measurements
        return result.measurements + ("image" to mapOf("mimeType" to "image/png", "data" to image))
    }
}
