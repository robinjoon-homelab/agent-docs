package xyz.robinjoon.agentdocs.workspace.domain.upload

import java.time.Instant

data class UploadTerm(
    val expiresAt: Instant,
    val usedAt: Instant?,
)
