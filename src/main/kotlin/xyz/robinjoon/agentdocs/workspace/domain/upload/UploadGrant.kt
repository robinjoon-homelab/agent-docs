package xyz.robinjoon.agentdocs.workspace.domain.upload

import xyz.robinjoon.agentdocs.domain.AggregateRoot
import xyz.robinjoon.agentdocs.workspace.domain.Actor
import java.time.Duration
import java.time.Instant
import java.util.UUID

// One file an agent may send from its shell. A model cannot attach a local file to a tool call, and base64 it writes
// out by hand gets mistyped, so the shell sends the file instead and this says it may.
@AggregateRoot
@ConsistentCopyVisibility
data class UploadGrant private constructor(
    val id: UploadGrantId,
    val target: UploadTarget,
    val secretHash: UploadSecretHash,
    val term: UploadTerm,
) {
    fun acceptsFrom(
        actor: Actor,
        at: Instant,
    ): Boolean = isOpenAt(at) && target.issuer.isSameAuthorAs(actor)

    fun isOpenTo(
        connection: UUID,
        at: Instant,
    ): Boolean = isOpenAt(at) && target.connectionId == connection

    private fun isOpenAt(at: Instant): Boolean = term.usedAt == null && at < term.expiresAt

    fun used(at: Instant): UploadGrant = copy(term = term.copy(usedAt = at))

    companion object {
        val lifetime: Duration = Duration.ofMinutes(10)

        fun issue(
            id: UploadGrantId,
            target: UploadTarget,
            secretHash: UploadSecretHash,
            at: Instant,
        ): UploadGrant = UploadGrant(id, target, secretHash, UploadTerm(at.plus(lifetime), null))

        fun restore(
            id: UploadGrantId,
            target: UploadTarget,
            secretHash: UploadSecretHash,
            term: UploadTerm,
        ): UploadGrant = UploadGrant(id, target, secretHash, term)
    }
}
