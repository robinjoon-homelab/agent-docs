package xyz.robinjoon.agentdocs.access.application.model

import xyz.robinjoon.agentdocs.access.domain.membership.SpaceRole
import java.util.UUID

// The owner's request to let a GitHub login into one space with a role. login is checked as a GitHub login by the use
// case, so the web layer passes it as given.
data class MembershipInvite(
    val space: UUID,
    val login: String,
    val role: SpaceRole,
)
