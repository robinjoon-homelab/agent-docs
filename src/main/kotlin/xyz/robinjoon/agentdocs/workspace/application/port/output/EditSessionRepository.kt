package xyz.robinjoon.agentdocs.workspace.application.port.output

import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.editsession.EditSession
import java.util.UUID

interface EditSessionRepository {
    fun lock(scope: String)

    fun open(scope: String): EditSession?

    fun inScope(scope: String): List<EditSession>

    fun find(id: UUID): EditSession?

    fun save(session: EditSession): EditSession

    fun list(document: DocumentId): List<EditSession>

    fun opened(): List<EditSession>

    fun adopt(
        document: DocumentId,
        version: Int,
        batch: UUID,
    ): Boolean

    fun replay(
        scope: String,
        operation: String,
    ): EditSession?

    fun remember(
        scope: String,
        operation: String,
        session: EditSession,
    ): EditSession
}
