package xyz.robinjoon.agentdocs.workspace.domain.editsession

import java.time.Instant
import java.util.UUID

class InputTrace(
    val tabId: UUID,
    val batchId: UUID,
    intervals: List<InputInterval>,
) {
    val intervals: List<InputInterval> = InputInterval.merge(intervals)
    val firstInputAt: Instant get() = this.intervals.first().firstInputAt
    val lastInputAt: Instant get() = this.intervals.last().lastInputAt

    init {
        require(this.intervals.isNotEmpty()) { "At least one actual input interval is required" }
    }

    constructor(tabId: UUID, batchId: UUID, firstInputAt: Instant, lastInputAt: Instant) :
        this(tabId, batchId, listOf(InputInterval(firstInputAt, lastInputAt)))

    fun requireValidAt(now: Instant) {
        require(!lastInputAt.isAfter(now)) { "Input timestamp is in the future" }
    }
}
