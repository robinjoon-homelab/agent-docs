package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import tools.jackson.databind.JsonNode
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.DesignJsonReader
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.design.DesignAddress
import xyz.robinjoon.agentdocs.workspace.domain.document.design.DesignVertexPath
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.DesignField
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.DesignFieldPatch
import java.math.BigDecimal
import java.net.URI

internal class DesignRequest(
    node: JsonNode,
) {
    private val fields = DesignJsonReader(node.toString()).read()
    private val link = DesignSelectionLink.from(optionalString("document"))
    val screen = (optionalString("screen") ?: link?.screen)?.let(::BlockId)
    val state = (optionalString("state") ?: link?.state?.takeIf { screen?.value == link.screen })?.let(::BlockId)
    val selected = link?.target?.takeIf { screen?.value == link.screen && state?.value == link.state }

    fun document(): DocumentId {
        val input = requiredString("document")
        val path = if (input.contains('/')) URI.create(input).path.substringAfterLast("/d/") else input
        return DocumentId(Wire.identifier(path, "document"))
    }

    fun optionalString(key: String): String? {
        val value = fields[key] ?: return null
        return (value as? String ?: throw InvalidRequestException("$key must be a string")).takeIf(String::isNotBlank)
    }

    fun requiredString(key: String): String = optionalString(key) ?: throw InvalidRequestException("$key is required")

    fun limit(): Int {
        val raw = fields["limit"] ?: return 12
        val number = raw as? BigDecimal ?: throw InvalidRequestException("limit must be an integer")
        val size =
            try {
                number.intValueExact()
            } catch (error: ArithmeticException) {
                throw InvalidRequestException("limit must be an integer", cause = error)
            }
        if (size !in 1..30) throw InvalidRequestException("limit must be 1..30")
        return size
    }

    fun target(): DesignAddress {
        val address = fields["target"]?.let { DesignAddress.from(it as? Map<*, *> ?: throw InvalidRequestException("invalid target")) }
        val selectedTarget = link?.target
        if (selectedTarget != null && address != null && selectedTarget != address) {
            throw InvalidRequestException("target differs from the supplied layer URL", "read that layer or use the intended layer URL")
        }
        return address ?: selectedTarget ?: throw InvalidRequestException("target or a selected layer URL is required")
    }

    fun patch(): DesignFieldPatch {
        val scope = screen ?: throw InvalidRequestException("screen is required")
        val destination = state ?: throw InvalidRequestException("state is required")
        if (link?.target != null && (scope.value != link.screen || destination.value != link.state)) {
            throw InvalidRequestException("screen or state differs from the selected layer URL", "copy the link from the intended state")
        }
        val value = fields["value"] ?: throw InvalidRequestException("value is required")
        val vertex = if ("parts" in fields) DesignVertexPath.from(fields["parts"]) else null
        return DesignFieldPatch(scope, destination, target(), guards(), DesignField(requiredString("path"), value, vertex))
    }

    private fun guards(): Map<BlockId, String> {
        val raw = fields["guards"] as? Map<*, *> ?: throw InvalidRequestException("guards are required; read nodes with state")
        if (raw.isEmpty()) throw InvalidRequestException("guards must not be empty")
        return raw.entries.associate {
            BlockId(it.key.toString()) to
                (it.value as? String ?: throw InvalidRequestException("invalid guard hash"))
        }
    }
}
