package xyz.robinjoon.agentdocs.workspace.application.model

import java.time.Instant
import java.util.UUID

// What an agent needs to send one file from its shell. The secret exists only here; the grant keeps its hash.
data class UploadPass(
    val connectionId: UUID,
    val secret: String,
    val expiresAt: Instant,
)
