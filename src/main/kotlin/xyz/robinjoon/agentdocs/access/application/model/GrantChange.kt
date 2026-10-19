package xyz.robinjoon.agentdocs.access.application.model

import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionScope
import java.util.UUID

// null keeps the current value.
data class GrantChange(
    val spaces: Set<UUID>?,
    val scope: ConnectionScope?,
)
