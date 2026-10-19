package xyz.robinjoon.agentdocs.access.application.model

import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionScope
import java.util.UUID

data class ScriptTokenRequest(
    val name: String,
    val spaces: Set<UUID>,
    val scope: ConnectionScope,
    val lifetimeDays: Int,
)
