package xyz.robinjoon.agentdocs.workspace.domain.editsession

import java.time.Instant

data class InputInterval(
    val firstInputAt: Instant,
    val lastInputAt: Instant,
) {
    init {
        require(!lastInputAt.isBefore(firstInputAt)) { "Input timestamps are reversed" }
    }

    companion object {
        fun merge(intervals: List<InputInterval>): List<InputInterval> {
            val result = mutableListOf<InputInterval>()
            for (next in intervals.sortedBy { it.firstInputAt }) {
                val previous = result.lastOrNull()
                if (previous != null && next.firstInputAt.isBefore(previous.lastInputAt.plus(EditSession.humanIdle))) {
                    result[result.lastIndex] = InputInterval(previous.firstInputAt, maxOf(previous.lastInputAt, next.lastInputAt))
                } else {
                    result.add(next)
                }
            }
            return result.toList()
        }
    }
}
