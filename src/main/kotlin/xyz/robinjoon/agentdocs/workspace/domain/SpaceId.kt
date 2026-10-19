package xyz.robinjoon.agentdocs.workspace.domain

import java.util.UUID

// The stable identity of a space. Documents, threads, assets, and agent connections refer to it, so renaming a
// space or changing its address never moves anything out of it.
@JvmInline
value class SpaceId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()
}
