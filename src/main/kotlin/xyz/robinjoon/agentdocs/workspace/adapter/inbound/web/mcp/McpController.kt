package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.CallerAccess
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.domain.document.ChangeSource
import java.security.Principal

// The remote MCP endpoint. Authentication happens before this controller; an unauthenticated request never gets here
// and is answered with 401 and the resource metadata location. A browser request from another origin is refused.
@RestController
class McpController(
    private val protocol: McpProtocol,
    private val settings: McpSettings,
) {
    @PostMapping("/mcp", consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun post(
        @RequestBody message: JsonNode,
        @RequestHeader headers: HttpHeaders,
        principal: Principal?,
    ): ResponseEntity<Map<String, Any?>> {
        val origin = headers.origin
        if (origin != null && origin !in settings.allowedOrigins) return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        val mcpHeaders = McpHeaders(headers.getFirst("MCP-Protocol-Version"), headers.getFirst("Mcp-Method"))
        val response = protocol.handle(message, mcpHeaders) { WriteContext(CallerAccess.of(principal), ChangeSource.MCP, null, null) }
        return ResponseEntity.status(response.status).body(response.body)
    }

    // No server-to-client stream is offered, in either protocol era.
    @GetMapping("/mcp")
    fun stream(): ResponseEntity<Unit> = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).header(HttpHeaders.ALLOW, "POST").build()

    @DeleteMapping("/mcp")
    fun end(): ResponseEntity<Unit> = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).header(HttpHeaders.ALLOW, "POST").build()
}
