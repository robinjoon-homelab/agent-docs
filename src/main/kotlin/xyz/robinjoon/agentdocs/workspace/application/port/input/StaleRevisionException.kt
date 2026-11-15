package xyz.robinjoon.agentdocs.workspace.application.port.input

import xyz.robinjoon.agentdocs.workspace.domain.document.Document

class StaleRevisionException(
    base: Int,
    current: Int,
    val document: Document? = null,
) : RuntimeException("the change was based on version $base but the document is at version $current")
