package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import tools.jackson.databind.JsonNode
import xyz.robinjoon.agentdocs.workspace.application.model.AssigneeChange
import xyz.robinjoon.agentdocs.workspace.application.model.FlowQuery
import xyz.robinjoon.agentdocs.workspace.application.model.HeaderChange
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.MarkdownFormat
import xyz.robinjoon.agentdocs.workspace.domain.ActorKind
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentStatus
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadStatus
import java.util.UUID

// Small readers for request JSON where a missing field and a null field mean different things.
internal object Wire {
    fun markdownFormat(value: String?): MarkdownFormat =
        when (value ?: "markdown") {
            "markdown" -> MarkdownFormat.ANNOTATED
            "plain" -> MarkdownFormat.PLAIN
            "outline" -> MarkdownFormat.OUTLINE
            else -> throw InvalidRequestException("format must be markdown, plain, or outline")
        }

    // A flow query lists candidates in its own outline-like form, so only an omitted format or outline goes with it.
    fun markdownFormat(
        value: String?,
        flowQuery: FlowQuery?,
    ): MarkdownFormat {
        if (flowQuery == null) return markdownFormat(value)
        if (value != null && value != "outline") {
            throw InvalidRequestException(
                "a flow query lists candidates; omit format or use outline",
                "read a candidate with blocks or section afterwards",
            )
        }
        return MarkdownFormat.OUTLINE
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

    fun strings(node: JsonNode?): List<String> {
        if (node == null || node.isNull) return emptyList()
        if (!node.isArray || node.values().any { !it.isString }) throw InvalidRequestException("Expected an array of strings")
        return node.values().map(JsonNode::stringValue)
    }

    fun required(
        node: JsonNode,
        field: String,
    ): String = optional(node, field) ?: throw InvalidRequestException("$field is required")

    fun optional(
        node: JsonNode,
        field: String,
    ): String? {
        val value = node.get(field) ?: return null
        if (value.isNull) return null
        return value.takeIf(JsonNode::isString)?.stringValue() ?: throw InvalidRequestException("$field must be a string")
    }

    fun identifier(
        value: String,
        field: String,
    ): UUID =
        try {
            UUID.fromString(value).also {
                if (!it.toString().equals(value, ignoreCase = true)) throw IllegalArgumentException("UUID must be canonical")
            }
        } catch (exception: IllegalArgumentException) {
            throw InvalidRequestException("$field must be a UUID", cause = exception)
        }

    fun requireObject(body: JsonNode) {
        if (!body.isObject) throw InvalidRequestException("Expected a JSON object")
    }

    // An explicit "assignee": null clears the assignee; leaving it out keeps it.
    fun headerChange(body: JsonNode): HeaderChange =
        HeaderChange(
            title = optional(body, "title"),
            path = optional(body, "path")?.let(::DocumentPath),
            status = optional(body, "status")?.let(::status),
            tags = body.get("tags")?.let(::strings),
            assignee = if (body.has("assignee")) AssigneeChange(optional(body, "assignee")) else null,
            baseVersion =
                body.get("baseVersion")?.takeIf(JsonNode::isInt)?.intValue()
                    ?: throw InvalidRequestException("baseVersion is required"),
        )
}
