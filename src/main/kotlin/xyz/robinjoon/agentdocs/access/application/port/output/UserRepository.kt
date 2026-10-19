package xyz.robinjoon.agentdocs.access.application.port.output

import xyz.robinjoon.agentdocs.access.domain.user.User
import xyz.robinjoon.agentdocs.access.domain.user.UserId

interface UserRepository {
    fun add(user: User): User

    fun update(user: User): User

    fun findById(id: UserId): User?

    fun findByGithubId(githubId: Long): User?
}
