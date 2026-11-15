package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import xyz.robinjoon.agentdocs.workspace.application.port.output.EditSessionRepository
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.editsession.EditPeriod
import xyz.robinjoon.agentdocs.workspace.domain.editsession.EditSession
import xyz.robinjoon.agentdocs.workspace.domain.editsession.EditSessionTarget
import java.util.UUID

class ExposedEditSessionRepository : EditSessionRepository {
    override fun lock(scope: String) {
        EditSessionScopesTable.insertIgnore { it[EditSessionScopesTable.scope] = scope }
        EditSessionScopesTable
            .selectAll()
            .where { EditSessionScopesTable.scope eq scope }
            .forUpdate()
            .single()
    }

    override fun open(scope: String): EditSession? =
        EditSessionsTable
            .selectAll()
            .where { (EditSessionsTable.scope eq scope) and EditSessionsTable.endedAt.isNull() }
            .singleOrNull()
            ?.toSession()

    override fun inScope(scope: String): List<EditSession> =
        EditSessionsTable.selectAll().where { EditSessionsTable.scope eq scope }.map { it.toSession() }

    override fun find(id: UUID): EditSession? =
        EditSessionsTable
            .selectAll()
            .where { EditSessionsTable.id eq id }
            .singleOrNull()
            ?.toSession()

    override fun save(session: EditSession): EditSession {
        val existing = find(session.id)
        if (existing == null) {
            EditSessionsTable.insert { write(it, session) }
        } else {
            EditSessionsTable.update({ EditSessionsTable.id eq session.id }) { write(it, session) }
        }
        return requireNotNull(find(session.id))
    }

    override fun list(document: DocumentId): List<EditSession> =
        EditSessionsTable.selectAll().where { EditSessionsTable.documentId eq document.value }.map { it.toSession() }

    override fun opened(): List<EditSession> =
        EditSessionsTable.selectAll().where { EditSessionsTable.endedAt.isNull() }.map { it.toSession() }

    override fun adopt(
        document: DocumentId,
        version: Int,
        batch: UUID,
    ): Boolean =
        EditSessionInputsTable
            .insertIgnore {
                it[documentId] = document.value
                it[EditSessionInputsTable.version] = version
                it[batchId] = batch
            }.insertedCount == 1

    override fun replay(
        scope: String,
        operation: String,
    ): EditSession? {
        val row =
            EditSessionOperationsTable
                .selectAll()
                .where { (EditSessionOperationsTable.scope eq scope) and (EditSessionOperationsTable.operation eq operation) }
                .singleOrNull() ?: return null
        val original = requireNotNull(find(row[EditSessionOperationsTable.sessionId]))
        val period =
            EditPeriod(
                row[EditSessionOperationsTable.startedAt].toInstant(),
                row[EditSessionOperationsTable.lastActivityAt].toInstant(),
                row[EditSessionOperationsTable.endedAt]?.toInstant(),
                row[EditSessionOperationsTable.reason],
            )
        return EditSession.restore(original.id, original.target, period)
    }

    override fun remember(
        scope: String,
        operation: String,
        session: EditSession,
    ): EditSession {
        EditSessionOperationsTable.insert {
            it[EditSessionOperationsTable.scope] = scope
            it[EditSessionOperationsTable.operation] = operation
            it[sessionId] = session.id
            it[startedAt] = session.period.startedAt.utc()
            it[lastActivityAt] = session.period.lastActivityAt.utc()
            it[endedAt] = session.period.endedAt?.utc()
            it[reason] = session.period.reason
        }
        return session
    }

    private fun write(
        statement: UpdateBuilder<*>,
        session: EditSession,
    ) {
        statement[EditSessionsTable.id] = session.id
        statement[EditSessionsTable.scope] = session.target.scope
        statement[EditSessionsTable.documentId] = session.target.documentId.value
        statement[EditSessionsTable.spaceId] = session.target.spaceId.value
        EditSessionsTable.actor.write(statement, session.target.actor)
        statement[EditSessionsTable.tabId] = session.target.tabId
        statement[EditSessionsTable.startedAt] = session.period.startedAt.utc()
        statement[EditSessionsTable.lastActivityAt] = session.period.lastActivityAt.utc()
        statement[EditSessionsTable.endedAt] = session.period.endedAt?.utc()
        statement[EditSessionsTable.reason] = session.period.reason
        statement[EditSessionsTable.supersededBy] = session.supersededBy
    }

    private fun ResultRow.toSession(): EditSession =
        EditSession.restore(
            this[EditSessionsTable.id],
            EditSessionTarget(
                DocumentId(this[EditSessionsTable.documentId]),
                SpaceId(this[EditSessionsTable.spaceId]),
                EditSessionsTable.actor.read(this),
                this[EditSessionsTable.tabId],
            ),
            EditPeriod(
                this[EditSessionsTable.startedAt].toInstant(),
                this[EditSessionsTable.lastActivityAt].toInstant(),
                this[EditSessionsTable.endedAt]?.toInstant(),
                this[EditSessionsTable.reason],
            ),
            this[EditSessionsTable.supersededBy],
        )
}
