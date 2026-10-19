package xyz.robinjoon.agentdocs.workspace.application.service

import xyz.robinjoon.agentdocs.application.model.ActivityActor
import xyz.robinjoon.agentdocs.application.model.ActivityActorKind
import xyz.robinjoon.agentdocs.application.model.ActivityRecord
import xyz.robinjoon.agentdocs.application.model.ActivitySubject
import xyz.robinjoon.agentdocs.application.port.output.ActivityReporter
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.OperationKey
import xyz.robinjoon.agentdocs.workspace.application.model.OperationRecord
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.application.port.output.OperationStore
import xyz.robinjoon.agentdocs.workspace.domain.Actor
import java.time.Clock
import java.time.Instant

// What every write does besides its own change: read the clock, append to the activity log, and remember the result
// of a write that carries an operation id. Services call it inside their own transaction.
class WriteJournal(
    private val activity: ActivityReporter,
    private val operations: OperationStore,
    private val clock: Clock,
) {
    fun now(): Instant = Instant.now(clock)

    // The record of an earlier write with the same operation id by the same writer, or null for a first attempt.
    fun replayed(
        context: WriteContext,
        kind: String,
    ): OperationRecord? {
        val record = keyOf(context)?.let(operations::find) ?: return null
        if (record.kind != kind) {
            throw InvalidRequestException(
                "opId ${context.operationId} was already used for a different operation",
                "use a new opId for each write",
            )
        }
        return record
    }

    fun remember(
        context: WriteContext,
        record: OperationRecord,
    ) {
        keyOf(context)?.let { operations.save(it, record, now()) }
    }

    fun record(
        context: WriteContext,
        kind: String,
        subject: ActivitySubject,
        summary: String,
    ) {
        activity.record(ActivityRecord(kind, actorOf(context.access.actor), subject, summary.take(SUMMARY_LENGTH), now()))
    }

    fun recordBySystem(
        kind: String,
        subject: ActivitySubject,
        summary: String,
    ) {
        activity.record(ActivityRecord(kind, ActivityActor.system, subject, summary.take(SUMMARY_LENGTH), now()))
    }

    private fun keyOf(context: WriteContext): OperationKey? {
        val actor = context.access.actor
        val writer = actor.agent?.connectionId ?: actor.userId
        return context.operationId?.let { OperationKey(writer.toString(), it) }
    }

    private fun actorOf(actor: Actor): ActivityActor =
        ActivityActor(
            kind = if (actor.isAgent) ActivityActorKind.AGENT else ActivityActorKind.HUMAN,
            userId = actor.userId,
            login = actor.login,
            connectionId = actor.agent?.connectionId,
            clientName = actor.agent?.clientName,
        )

    private companion object {
        const val SUMMARY_LENGTH = 300
    }
}
