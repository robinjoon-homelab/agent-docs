package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import tools.jackson.databind.JsonNode
import xyz.robinjoon.agentdocs.workspace.application.model.CodeReferenceRequest
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.domain.document.history.ChangeOrigin

object RevisionMetadataJson {
    fun context(
        context: WriteContext,
        body: JsonNode,
    ): WriteContext =
        context.copy(
            codeReferences =
                referenceNodes(body).map {
                    CodeReferenceRequest(
                        optionalString(it, "repository"),
                        optionalString(it, "commit"),
                        it.get("pullRequest")?.takeUnless { it.isNull }?.let { number ->
                            if (!number.isInt) {
                                throw InvalidRequestException(
                                    "pullRequest must be an integer",
                                )
                            }
                            number.intValue()
                        },
                    )
                },
        )

    private fun optionalString(
        node: JsonNode,
        field: String,
    ): String? {
        if (!node.isObject) throw InvalidRequestException("code reference must be an object")
        val value = node.get(field)?.takeUnless { it.isNull } ?: return null
        if (!value.isString) throw InvalidRequestException("$field must be a string")
        return value.stringValue()
    }

    private fun referenceNodes(body: JsonNode): List<JsonNode> {
        val value = body.get("codeReferences") ?: return emptyList()
        if (!value.isArray || value.size() > 20) throw InvalidRequestException("codeReferences must be an array of at most 20 references")
        return value.values().toList()
    }

    fun fields(origin: ChangeOrigin): Map<String, Any?> =
        mapOf(
            "codeReferences" to
                origin.codeReferences.map {
                    mapOf(
                        "repository" to it.repository,
                        "commit" to it.commit,
                        "pullRequest" to it.pullRequest,
                        "commitUrl" to it.commitUrl,
                        "pullRequestUrl" to it.pullRequestUrl,
                    )
                },
            "importProvenance" to origin.importProvenance?.let { mapOf("tool" to it.tool, "originalPath" to it.originalPath) },
        )
}
