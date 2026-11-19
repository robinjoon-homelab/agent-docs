package xyz.robinjoon.agentdocs.workspace.application.service

import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.workspace.application.model.EditStatistics
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.SavedEditInput
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.input.EditSessionUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.EditSessionRepository
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.Permission
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.editsession.EditEndReason
import xyz.robinjoon.agentdocs.workspace.domain.editsession.EditSession
import xyz.robinjoon.agentdocs.workspace.domain.editsession.EditSessionTarget
import xyz.robinjoon.agentdocs.workspace.domain.editsession.HumanEditSessions
import xyz.robinjoon.agentdocs.workspace.domain.editsession.InputTrace
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Transactional(propagation = Propagation.REQUIRES_NEW)
class EditSessionService(
    private val sessions: EditSessionRepository,
    private val documents: DocumentRepository,
    private val clock: Clock,
) : EditSessionUseCase {
    override fun recordHuman(
        access: Access,
        input: SavedEditInput,
    ): List<EditSession> {
        val document = visible(access, input.documentId)
        Permissions.require(access, document.spaceId, Permission.EDIT, "recording edit time")
        require(!access.actor.isAgent) { "Only a human may report input" }
        if (!input.changed) return emptyList()
        input.batch.requireValidAt(clock.instant())
        require(input.version in 1..document.version) { "Input version is not stored" }
        val foregrounds = input.batch.foregrounds()
        val targets = foregrounds.map { EditSessionTarget(document.id, document.spaceId, access.actor, it.tabId) }
        targets.map { it.scope }.sorted().forEach { sessions.lock(it) }
        if (!sessions.adopt(document.id, input.version, input.batch.batchId)) return emptyList()
        return targets.zip(foregrounds).flatMap { (target, trace) -> adoptInput(target, trace) }
    }

    private fun adoptInput(
        target: EditSessionTarget,
        trace: InputTrace,
    ): List<EditSession> {
        val changes = HumanEditSessions.adopt(target, sessions.inScope(target.scope), trace, clock.instant())
        return changes.map { sessions.save(it) }
    }

    override fun closeHuman(
        access: Access,
        document: DocumentId,
        tab: UUID,
        lastInputAt: Instant,
    ): EditSession? {
        val target = EditSessionTarget(document, visible(access, document).spaceId, access.actor, tab)
        require(!access.actor.isAgent) { "Only a human may close human input" }
        sessions.lock(target.scope)
        val current = sessions.open(target.scope) ?: return null
        // A delayed pagehide cannot close input which this request never observed.
        return if (current.period.lastActivityAt == lastInputAt) {
            sessions.save(current.close(lastInputAt, EditEndReason.HUMAN))
        } else {
            current
        }
    }

    override fun start(
        context: WriteContext,
        document: DocumentId,
    ): EditSession {
        requireAgent(context)
        val target = EditSessionTarget(document, visible(context.access, document).spaceId, context.access.actor, null)
        val namespace = operationScope(context)
        sessions.lock(namespace)
        val operation = operation(context, "start")
        sessions.replay(namespace, operation)?.let { return replayVisible(context.access, it) }
        sessions.lock(target.scope)
        val current = sessions.open(target.scope)?.let { sessions.save(expired(it)) }
        val session = if (current?.isOpen == true) current.activity(clock.instant()) else EditSession.start(target, clock.instant())
        return sessions.remember(namespace, operation, sessions.save(session))
    }

    override fun end(
        context: WriteContext,
        session: UUID,
    ): EditSession {
        requireAgent(context)
        val found = sessions.find(session) ?: throw InvalidRequestException("Edit session not found")
        visible(context.access, found.target.documentId)
        require(found.target.actor.isSameAuthorAs(context.access.actor)) { "Edit session belongs to another connection" }
        val namespace = operationScope(context)
        sessions.lock(namespace)
        val operation = operation(context, "end")
        sessions.replay(namespace, operation)?.let { return replayVisible(context.access, it) }
        sessions.lock(found.target.scope)
        val current = sessions.find(session) ?: throw InvalidRequestException("Edit session not found")
        val ended = expired(current).close(clock.instant(), EditEndReason.EXPLICIT)
        return sessions.remember(namespace, operation, sessions.save(ended))
    }

    override fun activity(
        access: Access,
        document: DocumentId,
    ) {
        if (!access.actor.isAgent) return
        val currentDocument = visible(access, document)
        Permissions.require(access, currentDocument.spaceId, Permission.EDIT, "updating edit time")
        val target = EditSessionTarget(document, currentDocument.spaceId, access.actor, null)
        sessions.lock(target.scope)
        val current = sessions.open(target.scope) ?: return
        sessions.save(expired(current).activity(clock.instant()))
    }

    override fun statistics(
        access: Access,
        document: DocumentId,
    ): EditStatistics {
        visible(access, document)
        return EditStatistics.of(sessions.list(document).map { expired(it) })
    }

    override fun expire() {
        for (candidate in sessions.opened().sortedBy { it.target.scope }) {
            sessions.lock(candidate.target.scope)
            val current = sessions.find(candidate.id) ?: continue
            if (current.expired(clock.instant())) sessions.save(expired(current))
        }
    }

    private fun expired(session: EditSession): EditSession =
        if (session.expired(clock.instant())) {
            session.close(session.period.lastActivityAt, if (session.target.actor.isAgent) EditEndReason.ESTIMATED else EditEndReason.HUMAN)
        } else {
            session
        }

    private fun visible(
        access: Access,
        id: DocumentId,
    ): Document = documents.findById(id)?.takeIf { access.sees(it.spaceId) } ?: throw DocumentNotFoundException(id)

    private fun requireAgent(context: WriteContext) {
        if (!context.access.actor.isAgent) throw InvalidRequestException("An agent connection is required")
    }

    private fun replayVisible(
        access: Access,
        session: EditSession,
    ): EditSession {
        visible(access, session.target.documentId)
        sessions.lock(session.target.scope)
        val current = sessions.find(session.id)
        if (current?.isOpen == true) sessions.save(expired(current).activity(clock.instant()))
        return session
    }

    private fun operationScope(context: WriteContext): String =
        "agent:${context.access.actor.userId}:${context.access.actor.agent?.connectionId}"

    private fun operation(
        context: WriteContext,
        action: String,
    ): String = "$action:${context.operationId ?: throw InvalidRequestException("opId is required")}"
}
