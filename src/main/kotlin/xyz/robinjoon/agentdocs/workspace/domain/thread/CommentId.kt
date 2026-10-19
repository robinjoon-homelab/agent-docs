package xyz.robinjoon.agentdocs.workspace.domain.thread

import java.util.UUID

@JvmInline
value class CommentId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()
}
