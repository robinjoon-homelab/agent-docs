package xyz.robinjoon.agentdocs.workspace.domain.editsession

import java.time.Instant

// Late successful input corrects human time facts, including intervals an idle scheduler already closed.
object HumanEditSessions {
    fun adopt(
        target: EditSessionTarget,
        existing: List<EditSession>,
        trace: InputTrace,
        now: Instant,
    ): List<EditSession> {
        require(!target.actor.isAgent && trace.tabId == target.tabId) { "Human input scope does not match" }
        val active = existing.filter { !it.isSuperseded }
        require(active.all { !it.target.actor.isAgent && it.target.scope == target.scope }) { "Cannot join different foreground scopes" }
        val components = InputInterval.merge(active.map(::interval) + trace.intervals)
        val changed =
            components
                .filter { component -> trace.intervals.any { within(it, component) } }
                .flatMap { component -> reconcile(target, active, component, now) }
        val updatedIds = changed.map { it.id }.toSet()
        val idle =
            active
                .filter { it.id !in updatedIds && it.expired(now) }
                .map { it.close(it.period.lastActivityAt, EditEndReason.HUMAN) }
        return idle + changed
    }

    private fun reconcile(
        target: EditSessionTarget,
        existing: List<EditSession>,
        component: InputInterval,
        now: Instant,
    ): List<EditSession> {
        val members = existing.filter { within(interval(it), component) }
        val canonical =
            members.minWithOrNull(compareBy<EditSession> { it.period.startedAt }.thenBy { it.id })
                ?: EditSession.start(target, component.firstInputAt)
        val corrected = canonical.correctHuman(component, now)
        return listOf(corrected) + members.filter { it.id != canonical.id }.map { it.supersede(canonical.id) }
    }

    private fun interval(session: EditSession): InputInterval = InputInterval(session.period.startedAt, session.period.lastActivityAt)

    private fun within(
        inner: InputInterval,
        outer: InputInterval,
    ): Boolean = !inner.firstInputAt.isBefore(outer.firstInputAt) && !inner.lastInputAt.isAfter(outer.lastInputAt)
}
