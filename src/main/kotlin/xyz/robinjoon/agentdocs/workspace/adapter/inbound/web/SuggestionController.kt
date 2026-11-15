package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode
import xyz.robinjoon.agentdocs.workspace.application.model.SuggestionRequest
import xyz.robinjoon.agentdocs.workspace.application.port.input.SuggestionUseCase
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentId
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId
import java.security.Principal
import java.util.UUID

@RestController
class SuggestionController(
    private val suggestions: SuggestionUseCase,
) {
    @PostMapping("/api/threads/{id}/suggestions")
    fun suggest(
        @PathVariable id: UUID,
        @RequestBody body: JsonNode,
        principal: Principal?,
    ): Map<String, Any?> {
        val request =
            SuggestionRequest(
                ThreadJson.anchorRequest(body.get("anchor")),
                Wire.optional(body, "hash"),
                Wire.required(body, "before"),
                Wire.optional(body, "after")
                    ?: throw xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException(
                        "after is required; use an empty string to remove text",
                    ),
            )
        return ThreadJson.thread(
            suggestions.suggest(CallerAccess.write(principal, Wire.optional(body, "opId"), null), ThreadId(id), request),
        )
    }

    @PostMapping("/api/comments/{id}/suggestion-decision")
    fun decide(
        @PathVariable id: UUID,
        @RequestBody body: JsonNode,
        principal: Principal?,
    ): Map<String, Any?> {
        val action = Wire.required(body, "action")
        if (action != "accept" &&
            action != "reject"
        ) {
            throw xyz.robinjoon.agentdocs.workspace.application.model
                .InvalidRequestException("action must be accept or reject")
        }
        return ThreadJson.thread(
            suggestions.decide(
                CallerAccess.write(principal, Wire.optional(body, "opId"), null),
                CommentId(id),
                action == "accept",
            ),
        )
    }
}
