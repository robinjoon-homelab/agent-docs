package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.inbox.InboxItem
import xyz.robinjoon.agentdocs.workspace.domain.space.Space

data class InboxView(
    val item: InboxItem,
    val space: Space,
    val documentTitle: String,
)
