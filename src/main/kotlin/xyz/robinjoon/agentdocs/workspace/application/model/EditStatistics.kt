package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.editsession.EditEndReason
import xyz.robinjoon.agentdocs.workspace.domain.editsession.EditSession
import java.time.Duration
import java.time.Instant

data class EditMeasure(
    val milliseconds: Long,
    val count: Int,
)

data class EditStatistics(
    val human: EditMeasure,
    val agentExplicit: EditMeasure,
    val agentEstimated: EditMeasure,
    val openCount: Int,
) {
    companion object {
        fun of(sessions: List<EditSession>): EditStatistics {
            val current = sessions.filter { !it.isSuperseded }
            val closed = current.filter { !it.isOpen }
            val human = closed.filter { !it.target.actor.isAgent }.groupBy { it.target.actor.userId }
            val merged = human.values.flatMap { union(it) }
            return EditStatistics(
                measure(merged),
                measure(closed.filter { it.period.reason == EditEndReason.EXPLICIT }.map(::interval)),
                measure(closed.filter { it.period.reason == EditEndReason.ESTIMATED }.map(::interval)),
                current.count { it.isOpen },
            )
        }

        private fun interval(session: EditSession): Pair<Instant, Instant> =
            session.period.startedAt to requireNotNull(session.period.endedAt)

        private fun measure(intervals: List<Pair<Instant, Instant>>): EditMeasure =
            EditMeasure(intervals.sumOf { Duration.between(it.first, it.second).toMillis() }, intervals.size)

        private fun union(sessions: List<EditSession>): List<Pair<Instant, Instant>> {
            val result = mutableListOf<Pair<Instant, Instant>>()
            for (next in sessions.map(::interval).sortedBy { it.first }) {
                val last = result.lastOrNull()
                if (last != null && !next.first.isAfter(last.second)) {
                    result[result.lastIndex] = last.first to maxOf(last.second, next.second)
                } else {
                    result.add(next)
                }
            }
            return result.toList()
        }
    }
}
