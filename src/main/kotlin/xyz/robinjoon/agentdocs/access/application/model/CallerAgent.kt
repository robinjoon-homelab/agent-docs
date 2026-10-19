package xyz.robinjoon.agentdocs.access.application.model

import java.io.Serializable
import java.util.UUID

data class CallerAgent(
    val connectionId: UUID,
    val clientName: String,
) : Serializable
