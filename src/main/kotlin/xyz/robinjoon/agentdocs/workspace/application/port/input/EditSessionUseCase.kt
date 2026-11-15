package xyz.robinjoon.agentdocs.workspace.application.port.input

import xyz.robinjoon.agentdocs.workspace.application.model.EditStatistics
import xyz.robinjoon.agentdocs.workspace.application.model.SavedEditInput
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.editsession.EditSession
import java.time.Instant
import java.util.UUID

interface EditSessionUseCase {
    fun recordHuman(
        access: Access,
        input: SavedEditInput,
    ): List<EditSession>

    fun closeHuman(
        access: Access,
        document: DocumentId,
        tab: UUID,
        lastInputAt: Instant,
    ): EditSession?

    fun start(
        context: WriteContext,
        document: DocumentId,
    ): EditSession

    fun end(
        context: WriteContext,
        session: UUID,
    ): EditSession

    fun activity(
        access: Access,
        document: DocumentId,
    )

    fun statistics(
        access: Access,
        document: DocumentId,
    ): EditStatistics

    fun expire()
}
