package xyz.robinjoon.agentdocs.workspace.application.port.input

import xyz.robinjoon.agentdocs.workspace.domain.Permission

// The caller sees the space but its permission is below what the action needs.
class PermissionDeniedException(
    val needed: Permission,
    action: String,
) : RuntimeException("$action needs ${needed.name.lowercase()} permission")
