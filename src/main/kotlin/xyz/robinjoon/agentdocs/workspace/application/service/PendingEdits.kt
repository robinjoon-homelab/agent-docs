package xyz.robinjoon.agentdocs.workspace.application.service

import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentRepository
import xyz.robinjoon.agentdocs.workspace.domain.Actor
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.history.Revision
import java.time.Instant

// Called with the document locked, inside the caller's write transaction.
class PendingEdits(
    private val documents: DocumentRepository,
    private val journal: WriteJournal,
) {
    fun confirm(document: Document): Document {
        val edit = documents.pending(document.id)
        val confirmed = documents.confirmPending(document)
        if (edit != null && confirmed.revision > document.revision) journal.confirmed(confirmed, edit.change)
        return confirmed
    }

    fun continues(
        edit: Revision,
        actor: Actor,
        now: Instant,
    ): Boolean =
        edit.change.origin.actor
            .isSameAuthorAs(actor) && !expired(edit, now)

    fun expired(
        edit: Revision,
        now: Instant,
    ): Boolean =
        !now.isBefore(
            edit.change.period.updatedAt
                .plusSeconds(30),
        ) ||
            !now.isBefore(
                edit.change.period.createdAt
                    .plusSeconds(600),
            )
}
