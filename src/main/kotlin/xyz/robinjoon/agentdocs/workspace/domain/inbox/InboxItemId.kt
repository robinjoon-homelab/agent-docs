package xyz.robinjoon.agentdocs.workspace.domain.inbox

import java.util.UUID

@JvmInline
value class InboxItemId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()
}
