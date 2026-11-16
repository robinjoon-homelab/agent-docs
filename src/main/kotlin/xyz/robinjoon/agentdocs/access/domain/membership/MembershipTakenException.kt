package xyz.robinjoon.agentdocs.access.domain.membership

// The space already has a live membership for that login or that person. existing is null when only the stored unique
// key noticed, which happens when two invitations race.
class MembershipTakenException(
    val existing: SpaceMembershipId?,
    cause: Throwable? = null,
) : RuntimeException("the space already has a live membership for this account", cause)
