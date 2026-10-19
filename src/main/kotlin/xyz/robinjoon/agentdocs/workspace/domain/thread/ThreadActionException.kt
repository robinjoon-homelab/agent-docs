package xyz.robinjoon.agentdocs.workspace.domain.thread

// An action the actor may not take on this thread, such as an agent resolving a thread a person opened. fix tells
// the caller what it can do instead.
class ThreadActionException(
    message: String,
    val fix: String,
) : RuntimeException(message)
