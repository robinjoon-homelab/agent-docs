package xyz.robinjoon.agentdocs.access.application.service

import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.access.application.model.Caller
import xyz.robinjoon.agentdocs.access.application.model.GitHubIdentity
import xyz.robinjoon.agentdocs.access.application.port.input.SignInRefusedException
import xyz.robinjoon.agentdocs.access.application.port.input.SignInUseCase
import xyz.robinjoon.agentdocs.access.application.port.output.UserRepository
import xyz.robinjoon.agentdocs.access.domain.user.Allowlist
import xyz.robinjoon.agentdocs.access.domain.user.GitHubAccount
import xyz.robinjoon.agentdocs.access.domain.user.User
import xyz.robinjoon.agentdocs.access.domain.user.UserId
import xyz.robinjoon.agentdocs.access.domain.user.UserProfile
import java.time.Clock
import java.util.UUID

// GitHub only says who the person is. Whether they may enter is decided here by the allowlist.
@Transactional(readOnly = true)
class SignInService(
    private val users: UserRepository,
    private val allowlist: Allowlist,
    private val clock: Clock,
) : SignInUseCase {
    @Transactional
    override fun signIn(identity: GitHubIdentity): Caller {
        val role = allowlist.roleOf(identity.login) ?: throw SignInRefusedException(identity.login)
        val account = GitHubAccount(identity.githubId, identity.login)
        val profile = UserProfile(identity.name, identity.avatarUrl)
        val now = clock.instant()
        val existing = users.findByGithubId(identity.githubId)
        val user =
            if (existing == null) {
                users.add(User.register(UserId(UUID.randomUUID()), account, profile, now))
            } else {
                users.update(existing.signedIn(account, profile, now))
            }
        return Callers.person(user, role)
    }
}
