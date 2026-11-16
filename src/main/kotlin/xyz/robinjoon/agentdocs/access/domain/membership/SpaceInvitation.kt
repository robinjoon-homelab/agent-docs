package xyz.robinjoon.agentdocs.access.domain.membership

import xyz.robinjoon.agentdocs.access.domain.GitHubLogin
import xyz.robinjoon.agentdocs.access.domain.user.UserId
import java.time.Instant
import java.util.UUID

// Who invited which GitHub login to which space, and when. The space is a workspace space id kept by value, as
// connection grants keep theirs; none of this changes after the invitation.
data class SpaceInvitation(
    val spaceId: UUID,
    val login: GitHubLogin,
    val invitedBy: UserId,
    val invitedAt: Instant,
)
