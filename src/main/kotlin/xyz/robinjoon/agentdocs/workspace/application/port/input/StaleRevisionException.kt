package xyz.robinjoon.agentdocs.workspace.application.port.input

// The writer based a change on a revision the document has already moved past.
class StaleRevisionException(
    base: Int,
    current: Int,
) : RuntimeException("the change was based on revision $base but the document is at revision $current")
