package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import tools.jackson.databind.JsonNode
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentWrite
import xyz.robinjoon.agentdocs.workspace.application.model.SavedEditInput
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.application.port.input.EditSessionUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.StaleRevisionException
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.PathTakenException
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.EditConflictException

// Called at the HTTP/MCP boundary after the document service has committed its independent write.
class EditStatisticsRecorder(
    private val sessions: EditSessionUseCase,
) {
    fun human(
        context: WriteContext,
        written: DocumentWrite,
        body: JsonNode,
    ): Boolean =
        EditStatisticsExceptionHandler.afterWrite {
            if (!context.access.actor.isAgent && !written.unchanged) {
                val trace = body.get("inputTrace")
                if (trace != null && !trace.isNull) {
                    sessions.recordHuman(
                        context.access,
                        SavedEditInput(written.document.id, written.document.version, true, EditStatisticsJson.inputBatch(trace)),
                    )
                }
            }
        }

    fun agent(
        access: Access,
        document: DocumentId,
    ): Boolean = EditStatisticsExceptionHandler.afterWrite { sessions.activity(access, document) }
}

// The command has already passed its authorization check when it reports a write conflict.
internal fun <T> EditStatisticsRecorder.countConflict(
    access: Access,
    document: DocumentId,
    write: () -> T,
): T =
    try {
        write()
    } catch (conflict: EditConflictException) {
        if (access.actor.isAgent) agent(access, document)
        throw conflict
    } catch (conflict: StaleRevisionException) {
        if (access.actor.isAgent) agent(access, document)
        throw conflict
    } catch (conflict: PathTakenException) {
        if (access.actor.isAgent) agent(access, document)
        throw conflict
    }
