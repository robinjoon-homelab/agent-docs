package xyz.robinjoon.agentdocs.access.application.service

import xyz.robinjoon.agentdocs.access.application.model.Caller
import xyz.robinjoon.agentdocs.application.model.ActivityActor
import xyz.robinjoon.agentdocs.application.model.ActivityActorKind
import xyz.robinjoon.agentdocs.application.model.ActivityRecord
import xyz.robinjoon.agentdocs.application.model.ActivitySubject
import xyz.robinjoon.agentdocs.application.port.output.ActivityReporter
import java.time.Clock
import java.time.Instant

// The clock and shared activity log for access changes.
class AccessJournal(
    private val activity: ActivityReporter,
    private val clock: Clock,
) {
    fun now(): Instant = Instant.now(clock)

    fun record(
        caller: Caller,
        kind: String,
        summary: String,
        subject: ActivitySubject = ActivitySubject(null, null, null, null),
    ) {
        val actor = ActivityActor(ActivityActorKind.HUMAN, caller.userId, caller.login, null, null)
        activity.record(ActivityRecord(kind, actor, subject, summary, now()))
    }
}
