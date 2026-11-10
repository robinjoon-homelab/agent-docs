package xyz.robinjoon.agentdocs.workspace.domain.upload

import java.util.UUID

@JvmInline
value class UploadGrantId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()
}
