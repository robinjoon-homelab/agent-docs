package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import tools.jackson.databind.JsonNode
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentWrite
import xyz.robinjoon.agentdocs.workspace.application.model.SavedEditInput
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.application.port.input.EditSessionUseCase
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId

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
