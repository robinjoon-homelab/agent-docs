package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

internal fun Instant.utc(): OffsetDateTime = atOffset(ZoneOffset.UTC)
