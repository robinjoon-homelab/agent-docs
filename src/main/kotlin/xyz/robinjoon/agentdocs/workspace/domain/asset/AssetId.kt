package xyz.robinjoon.agentdocs.workspace.domain.asset

import java.util.UUID

@JvmInline
value class AssetId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()
}
