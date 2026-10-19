package xyz.robinjoon.agentdocs.workspace.application.port.input

import xyz.robinjoon.agentdocs.workspace.domain.space.Space
import java.util.Collections

// No single space is linked to the repository or path. candidates lists the spaces that matched equally well, or
// every visible space when none matched, so the caller can pick one.
class SpaceNotResolvedException(
    message: String,
    candidates: List<Space>,
) : RuntimeException(message) {
    val candidates: List<Space> = Collections.unmodifiableList(candidates.toList())
}
