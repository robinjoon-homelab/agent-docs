package xyz.robinjoon.agentdocs.workspace.adapter.outbound.design

import tools.jackson.core.JacksonException
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import xyz.robinjoon.agentdocs.workspace.application.model.DesignValidationWarning
import xyz.robinjoon.agentdocs.workspace.application.port.output.FlowDesignValidator
import xyz.robinjoon.agentdocs.workspace.domain.document.InvalidDocumentException

class JsonFlowDesignValidator(
    json: ObjectMapper,
) : FlowDesignValidator {
    private val reader =
        json
            .reader()
            .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)

    override fun validate(
        source: String,
        previousSource: String?,
    ): List<DesignValidationWarning> {
        val candidate = parse(source)
        val previous = previousSource?.let(::parse)
        if (previous != null && previous.path("id") != candidate.path("id")) {
            throw InvalidDocumentException("the previous design must belong to the same document")
        }
        DesignSchema.validate(candidate)
        return DesignGraphRules.validate(candidate) + DesignOccurrenceRules.validate(candidate, previous)
    }

    private fun parse(source: String): JsonNode =
        try {
            reader.readTree(source)
        } catch (exception: JacksonException) {
            throw InvalidDocumentException("flow-design needs a single JSON document with unique object keys", exception)
        }
}
