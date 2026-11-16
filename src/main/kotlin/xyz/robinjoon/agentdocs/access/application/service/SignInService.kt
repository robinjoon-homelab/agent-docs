package xyz.robinjoon.agentdocs.access.application.service

import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.access.application.model.Caller
import xyz.robinjoon.agentdocs.access.application.model.GitHubIdentity
import xyz.robinjoon.agentdocs.access.application.port.input.SignInRefusedException
import xyz.robinjoon.agentdocs.access.application.port.input.SignInUseCase
import xyz.robinjoon.agentdocs.access.application.port.output.SpaceMembershipRepository
import xyz.robinjoon.agentdocs.access.application.port.output.UserRepository
import xyz.robinjoon.agentdocs.access.domain.GitHubLogin
import xyz.robinjoon.agentdocs.access.domain.membership.SpaceMembership
import xyz.robinjoon.agentdocs.access.domain.user.Allowlist
import xyz.robinjoon.agentdocs.access.domain.user.GitHubAccount
import xyz.robinjoon.agentdocs.access.domain.user.User
import xyz.robinjoon.agentdocs.access.domain.user.UserId
import xyz.robinjoon.agentdocs.access.domain.user.UserProfile
import xyz.robinjoon.agentdocs.access.domain.user.UserRole
import java.util.UUID

// GitHub only says who the person is. Whether they may enter is decided here: the allowlist, a live invitation of the
// login, or a membership already fixed to this GitHub id. A sign-in is the one moment GitHub has just confirmed that
// the login belongs to the id, so the waiting invitations of the login are fixed to the user here, and the user and
// those memberships commit together.
@Transactional(readOnly = true)
class SignInService(
    private val users: UserRepository,
    private val memberships: SpaceMembershipRepository,
    private val allowlist: Allowlist,
    private val journal: AccessJournal,
) : SignInUseCase {
    @Transactional
    override fun signIn(identity: GitHubIdentity): Caller {
        val account = GitHubAccount(identity.githubId, identity.login)
        val existing = users.findByGithubId(identity.githubId)
        val waiting = memberships.lockPendingByLogin(GitHubLogin(identity.login))
        val joined = existing?.let { memberships.findActiveByMember(it.id) }.orEmpty()
        val role =
            allowlist.roleOf(identity.login)
                ?: UserRole.MEMBER.takeIf { waiting.isNotEmpty() || joined.isNotEmpty() }
                ?: throw SignInRefusedException(identity.login)
        val user = saved(existing, account, UserProfile(identity.name, identity.avatarUrl))
        if (role == UserRole.OWNER) return Callers.person(user, role)
        val newlyJoined = join(user, waiting, joined)
        val caller = Callers.person(user, role, joined + newlyJoined)
        newlyJoined.forEach {
            val label = SpaceMembershipService.nameOf(it.role)
            journal.record(caller, "membership.joined", "초대받은 멤버 첫 로그인: @${user.account.login} $label")
        }
        return caller
    }

    private fun saved(
        existing: User?,
        account: GitHubAccount,
        profile: UserProfile,
    ): User {
        val now = journal.now()
        return if (existing == null) {
            users.add(User.register(UserId(UUID.randomUUID()), account, profile, now))
        } else {
            users.update(existing.signedIn(account, profile, now))
        }
    }

    // A space where this person is already a member keeps that membership; a second invitation to it stays waiting
    // for the owner to remove.
    private fun join(
        user: User,
        waiting: List<SpaceMembership>,
        joined: List<SpaceMembership>,
    ): List<SpaceMembership> {
        val taken = joined.map { it.spaceId }.toSet()
        val now = journal.now()
        return waiting
            .filter { it.spaceId !in taken }
            .mapNotNull { it.join(user.id, now) }
            .map(memberships::update)
    }
}
