package xyz.robinjoon.agentdocs.access.domain.connection

import java.util.Collections
import java.util.UUID

// The spaces a connection may see and what it may do there. An empty set sees nothing; a space created later never
// joins an existing grant.
@ConsistentCopyVisibility
data class Grant private constructor(
    val spaces: Set<UUID>,
    val scope: ConnectionScope,
) {
    companion object {
        fun of(
            spaces: Set<UUID>,
            scope: ConnectionScope,
        ): Grant = Grant(Collections.unmodifiableSet(LinkedHashSet(spaces)), scope)
    }
}
