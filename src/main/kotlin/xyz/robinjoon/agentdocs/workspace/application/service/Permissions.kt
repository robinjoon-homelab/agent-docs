package xyz.robinjoon.agentdocs.workspace.application.service

import xyz.robinjoon.agentdocs.workspace.application.port.input.PermissionDeniedException
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.Permission
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId

// Callers check visibility first and answer an invisible space as not found; these checks only run for a space the
// caller already sees.
internal object Permissions {
    fun require(
        access: Access,
        space: SpaceId,
        needed: Permission,
        action: String,
    ) {
        if (!access.allows(space, needed)) {
            throw PermissionDeniedException(needed, action)
        }
    }

    fun requireOwner(
        access: Access,
        action: String,
    ) {
        if (!access.isOwner) {
            throw PermissionDeniedException(Permission.OWNER, action)
        }
    }
}
