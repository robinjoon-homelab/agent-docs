package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.application.model.ActivitySubject
import xyz.robinjoon.agentdocs.application.model.CreatedComment

// An activity's facts, before the journal supplies its authenticated actor and timestamp.
data class WorkspaceActivity(
    val kind: String,
    val subject: ActivitySubject,
    val summary: String,
    val createdComment: CreatedComment? = null,
)
