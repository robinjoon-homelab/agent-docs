package xyz.robinjoon.agentdocs.workspace.domain.editsession

import java.time.Instant
import java.util.UUID

// One committed document version may contain pending input from several foreground segments.
class HumanInputBatch(
    val batchId: UUID,
    traces: List<InputTrace>,
) {
    val traces: List<InputTrace> = traces.toList()

    init {
        require(this.traces.isNotEmpty()) { "At least one foreground input trace is required" }
    }

    fun requireValidAt(now: Instant) {
        traces.forEach { it.requireValidAt(now) }
    }

    fun foregrounds(): List<InputTrace> =
        traces
            .groupBy { it.tabId }
            .map { (tab, children) ->
                InputTrace(tab, batchId, children.flatMap { it.intervals })
            }.sortedBy { it.tabId.toString() }

    companion object {
        fun single(trace: InputTrace): HumanInputBatch = HumanInputBatch(trace.batchId, listOf(trace))
    }
}
