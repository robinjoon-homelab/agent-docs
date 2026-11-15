package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId

// What to change in what a space points agents to. Null keeps the current setting.
data class GuidanceChange(
    val guide: SettingUpdate<DocumentId>?,
    val defaultRepository: SettingUpdate<String>?,
)
