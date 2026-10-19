package xyz.robinjoon.agentdocs.workspace.domain

import java.util.UUID

// The OAuth client or script token an agent acts through. The connection belongs to the access context; the
// workspace keeps only its identifier and display name.
data class AgentIdentity(
    val connectionId: UUID,
    val clientName: String,
)
