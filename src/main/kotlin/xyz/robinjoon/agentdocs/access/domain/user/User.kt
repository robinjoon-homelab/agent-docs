package xyz.robinjoon.agentdocs.access.domain.user

import xyz.robinjoon.agentdocs.domain.AggregateRoot
import java.time.Instant

// A person who signed in with GitHub at least once. The GitHub access token is never kept; the profile is refreshed on
// every sign-in so a renamed login shows up the next time.
@AggregateRoot
@ConsistentCopyVisibility
data class User private constructor(
    val id: UserId,
    val account: GitHubAccount,
    val profile: UserProfile,
    val lastSignedInAt: Instant,
) {
    fun signedIn(
        account: GitHubAccount,
        profile: UserProfile,
        at: Instant,
    ): User {
        require(account.githubId == this.account.githubId) { "a different GitHub account cannot sign in as this user" }
        return copy(account = account, profile = profile, lastSignedInAt = at)
    }

    companion object {
        fun register(
            id: UserId,
            account: GitHubAccount,
            profile: UserProfile,
            at: Instant,
        ): User = User(id, account, profile, at)

        fun restore(
            id: UserId,
            account: GitHubAccount,
            profile: UserProfile,
            lastSignedInAt: Instant,
        ): User = User(id, account, profile, lastSignedInAt)
    }
}
