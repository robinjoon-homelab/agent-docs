package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp

import tools.jackson.databind.JsonNode
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID

// Typed reads of a tool's arguments. A wrong or missing argument becomes an invalid-request tool error that names it.
class Arguments(
    val node: JsonNode,
) {
    fun string(name: String): String = optionalString(name) ?: throw InvalidRequestException("argument $name is required")

    fun optionalString(name: String): String? {
        val value = node.get(name)?.takeUnless(JsonNode::isNull) ?: return null
        if (!value.isString) throw InvalidRequestException("argument $name must be a string")
        return value.stringValue().takeIf(String::isNotEmpty)
    }

    fun uuid(name: String): UUID = uuidOf(name, string(name))

    fun optionalUuid(name: String): UUID? = optionalString(name)?.let { uuidOf(name, it) }

    fun optionalInt(name: String): Int? {
        val value = node.get(name)?.takeUnless(JsonNode::isNull) ?: return null
        if (!value.isInt) throw InvalidRequestException("argument $name must be an integer")
        return value.intValue()
    }

    fun boolean(name: String): Boolean {
        val value = node.get(name)?.takeUnless(JsonNode::isNull) ?: return false
        if (!value.isBoolean) throw InvalidRequestException("argument $name must be a boolean")
        return value.booleanValue()
    }

    fun strings(name: String): List<String>? {
        val value = node.get(name)?.takeUnless(JsonNode::isNull) ?: return null
        if (!value.isArray) throw InvalidRequestException("argument $name must be an array of strings")
        return value.values().map {
            if (!it.isString) throw InvalidRequestException("argument $name must be an array of strings")
            it.stringValue()
        }
    }

    fun optionalInstant(name: String): Instant? =
        optionalString(name)?.let { value ->
            try {
                Instant.parse(value)
            } catch (exception: DateTimeParseException) {
                throw InvalidRequestException("argument $name must be an ISO-8601 instant such as 2026-10-18T09:00:00Z", cause = exception)
            }
        }

    private fun uuidOf(
        name: String,
        value: String,
    ): UUID =
        try {
            UUID.fromString(value)
        } catch (exception: IllegalArgumentException) {
            throw InvalidRequestException("argument $name must be a UUID", cause = exception)
        }
}
