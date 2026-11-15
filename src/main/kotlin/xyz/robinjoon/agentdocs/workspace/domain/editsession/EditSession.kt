package xyz.robinjoon.agentdocs.workspace.domain.editsession

import xyz.robinjoon.agentdocs.domain.AggregateRoot
import java.time.Duration
import java.time.Instant
import java.util.UUID

@AggregateRoot
@ConsistentCopyVisibility
data class EditSession private constructor(
    val id: UUID,
    val target: EditSessionTarget,
    val period: EditPeriod,
    val supersededBy: UUID? = null,
) {
    init {
        require(supersededBy == null || !target.actor.isAgent) { "Agent results cannot be superseded" }
        require(period.reason == null || target.actor.isAgent == (period.reason != EditEndReason.HUMAN)) {
            "End reason does not match the editor kind"
        }
    }

    val isSuperseded: Boolean get() = supersededBy != null
    val isOpen: Boolean get() = period.endedAt == null && !isSuperseded

    fun expired(at: Instant): Boolean =
        isOpen && !at.isBefore(period.lastActivityAt.plus(if (target.actor.isAgent) agentIdle else humanIdle))

    fun input(trace: InputTrace): EditSession {
        check(isOpen && !target.actor.isAgent) { "Only an open human session accepts input" }
        require(trace.tabId == target.tabId && trace.intervals.size == 1) { "Adopt separate intervals through human reconciliation" }
        return copy(
            period =
                period.copy(
                    startedAt = minOf(period.startedAt, trace.firstInputAt),
                    lastActivityAt = maxOf(period.lastActivityAt, trace.lastInputAt),
                ),
        )
    }

    fun correctHuman(
        interval: InputInterval,
        now: Instant,
    ): EditSession {
        require(!target.actor.isAgent && !isSuperseded) { "Only current human intervals may be corrected" }
        val closed = !now.isBefore(interval.lastInputAt.plus(humanIdle))
        val next =
            EditPeriod(
                interval.firstInputAt,
                interval.lastInputAt,
                if (closed) interval.lastInputAt else null,
                if (closed) EditEndReason.HUMAN else null,
            )
        return copy(period = next)
    }

    fun supersede(canonical: UUID): EditSession {
        require(!target.actor.isAgent && canonical != id) { "Only distinct human intervals may be joined" }
        return copy(period = period.copy(endedAt = period.lastActivityAt, reason = EditEndReason.HUMAN), supersededBy = canonical)
    }

    fun activity(at: Instant): EditSession =
        if (isOpen && target.actor.isAgent) copy(period = period.copy(lastActivityAt = maxOf(period.lastActivityAt, at))) else this

    fun close(
        at: Instant,
        reason: EditEndReason,
    ): EditSession {
        if (!isOpen) return this
        val end = if (reason == EditEndReason.EXPLICIT) maxOf(period.lastActivityAt, at) else period.lastActivityAt
        return copy(period = period.copy(endedAt = end, reason = reason))
    }

    companion object {
        val humanIdle: Duration = Duration.ofSeconds(120)
        val agentIdle: Duration = Duration.ofMinutes(30)

        fun start(
            target: EditSessionTarget,
            at: Instant,
        ): EditSession = EditSession(UUID.randomUUID(), target, EditPeriod(at, at, null, null))

        fun restore(
            id: UUID,
            target: EditSessionTarget,
            period: EditPeriod,
            supersededBy: UUID? = null,
        ): EditSession = EditSession(id, target, period, supersededBy)
    }
}
