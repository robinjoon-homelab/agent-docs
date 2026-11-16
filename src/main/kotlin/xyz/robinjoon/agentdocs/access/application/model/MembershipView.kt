package xyz.robinjoon.agentdocs.access.application.model

import xyz.robinjoon.agentdocs.access.domain.membership.SpaceMembership

// A membership as the owner sees it. login is the member's current GitHub login once it is joined, since the person may
// have renamed the account; before that it is the login the owner invited.
data class MembershipView(
    val membership: SpaceMembership,
    val login: String,
)
