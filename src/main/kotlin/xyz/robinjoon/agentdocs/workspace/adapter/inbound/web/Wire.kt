package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import tools.jackson.databind.JsonNode
import xyz.robinjoon.agentdocs.workspace.application.model.AssigneeChange
import xyz.robinjoon.agentdocs.workspace.application.model.HeaderChange
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.MarkdownFormat
import xyz.robinjoon.agentdocs.workspace.domain.ActorKind
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentStatus
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadStatus

// Small readers for request JSON where a missing field and a null field mean different things.
internal object Wire {
    fun markdownFormat(value: String?): MarkdownFormat =
        when (value ?: "markdown") {
            "markdown" -> MarkdownFormat.ANNOTATED
            "plain" -> MarkdownFormat.PLAIN
            "outline" -> MarkdownFormat.OUTLINE
            else -> throw InvalidRequestException("format must be markdown, plain, or outline")
        }

    fun actorKind(
        value: String?,
        field: String = "actor",
    ): ActorKind? =
        value?.let { name ->
            ActorKind.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: throw InvalidRequestException("$field must be human or agent")
        }

    fun status(value: String): DocumentStatus =
        DocumentStatus.entries.firstOrNull { it.name.equals(value.replace('-', '_'), ignoreCase = true) }
            ?: throw InvalidRequestException("status must be draft, in_review, confirmed, or archived")

    fun threadStatus(value: String): ThreadStatus =
        ThreadStatus.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
            ?: throw InvalidRequestException("status must be open, answered, or resolved")

    fun strings(node: JsonNode?): List<String> = node?.values()?.mapNotNull { it.takeIf(JsonNode::isString)?.stringValue() }.orEmpty()

    fun required(
        node: JsonNode,
        field: String,
    ): String = optional(node, field) ?: throw InvalidRequestException("$field is required")

    fun optional(
        node: JsonNode,
        field: String,
    ): String? = node.get(field)?.takeIf(JsonNode::isString)?.stringValue()

    // An explicit "assignee": null clears the assignee; leaving it out keeps it.
    fun headerChange(body: JsonNode): HeaderChange =
        HeaderChange(
            title = optional(body, "title"),
            path = optional(body, "path")?.let(::DocumentPath),
            status = optional(body, "status")?.let(::status),
            tags = body.get("tags")?.let(::strings),
            assignee = if (body.has("assignee")) AssigneeChange(optional(body, "assignee")) else null,
            baseRevision = body.get("baseRevision")?.takeIf(JsonNode::isInt)?.intValue(),
        )
}
