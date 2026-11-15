package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import tools.jackson.databind.JsonNode
import xyz.robinjoon.agentdocs.workspace.application.model.EditStatistics
import xyz.robinjoon.agentdocs.workspace.domain.editsession.EditSession
import xyz.robinjoon.agentdocs.workspace.domain.editsession.HumanInputBatch
import xyz.robinjoon.agentdocs.workspace.domain.editsession.InputInterval
import xyz.robinjoon.agentdocs.workspace.domain.editsession.InputTrace
import java.time.Instant
import java.util.UUID

object EditStatisticsJson {
    fun session(session: EditSession): Map<String, Any?> =
        mapOf(
            "id" to session.id,
            "documentId" to session.target.documentId.value,
            "startedAt" to session.period.startedAt,
            "lastActivityAt" to session.period.lastActivityAt,
            "endedAt" to session.period.endedAt,
            "reason" to
                session.period.reason
                    ?.name
                    ?.lowercase(),
        )

    fun statistics(value: EditStatistics): Map<String, Any> =
        mapOf(
            "human" to value.human,
            "agentExplicit" to value.agentExplicit,
            "agentEstimated" to value.agentEstimated,
            "openCount" to value.openCount,
        )

    fun inputBatch(node: JsonNode): HumanInputBatch {
        val children = node.get("traces") ?: return HumanInputBatch.single(readTrace(node))
        require(children.isArray) { "inputTrace.traces must be an array" }
        return HumanInputBatch(UUID.fromString(Wire.required(node, "batchId")), children.values().map(::readTrace))
    }

    private fun readTrace(node: JsonNode): InputTrace {
        val intervals = node.get("intervals")
        require(intervals != null && intervals.isArray) { "inputTrace.intervals must be an array" }
        return InputTrace(
            UUID.fromString(Wire.required(node, "tabId")),
            UUID.fromString(Wire.required(node, "batchId")),
            intervals.values().map {
                InputInterval(
                    Instant.parse(Wire.required(it, "firstInputAt")),
                    Instant.parse(Wire.required(it, "lastInputAt")),
                )
            },
        )
    }
}
