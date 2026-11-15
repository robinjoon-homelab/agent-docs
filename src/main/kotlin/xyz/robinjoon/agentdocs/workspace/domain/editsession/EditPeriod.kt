package xyz.robinjoon.agentdocs.workspace.domain.editsession

import java.time.Instant

enum class EditEndReason { HUMAN, EXPLICIT, ESTIMATED }

data class EditPeriod(
    val startedAt: Instant,
    val lastActivityAt: Instant,
    val endedAt: Instant?,
    val reason: EditEndReason?,
) {
    init {
        require(!lastActivityAt.isBefore(startedAt)) { "Activity precedes the start" }
        require((endedAt == null) == (reason == null)) { "A closed session needs an end reason" }
        require(endedAt == null || !endedAt.isBefore(startedAt)) { "End precedes the start" }
    }
}
