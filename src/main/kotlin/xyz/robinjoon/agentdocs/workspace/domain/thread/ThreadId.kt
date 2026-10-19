package xyz.robinjoon.agentdocs.workspace.domain.thread

import java.util.UUID

@JvmInline
value class ThreadId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()
}
