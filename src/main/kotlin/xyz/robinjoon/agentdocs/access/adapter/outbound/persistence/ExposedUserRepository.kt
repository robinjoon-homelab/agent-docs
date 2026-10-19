package xyz.robinjoon.agentdocs.access.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import xyz.robinjoon.agentdocs.access.application.port.output.UserRepository
import xyz.robinjoon.agentdocs.access.domain.user.GitHubAccount
import xyz.robinjoon.agentdocs.access.domain.user.User
import xyz.robinjoon.agentdocs.access.domain.user.UserId
import xyz.robinjoon.agentdocs.access.domain.user.UserProfile
import java.time.ZoneOffset

class ExposedUserRepository : UserRepository {
    override fun add(user: User): User {
        UsersTable.insert {
            it[id] = user.id.value
            write(it, user)
        }
        return requireNotNull(findById(user.id))
    }

    override fun update(user: User): User {
        val updated = UsersTable.update({ UsersTable.id eq user.id.value }) { write(it, user) }
        check(updated == 1) { "User to update does not exist: ${user.id}" }
        return requireNotNull(findById(user.id))
    }

    override fun findById(id: UserId): User? =
        UsersTable
            .selectAll()
            .where { UsersTable.id eq id.value }
            .singleOrNull()
            ?.toUser()

    override fun findByGithubId(githubId: Long): User? =
        UsersTable
            .selectAll()
            .where {
                UsersTable.githubId eq githubId
            }.singleOrNull()
            ?.toUser()

    private fun write(
        statement: UpdateBuilder<*>,
        user: User,
    ) {
        statement[UsersTable.githubId] = user.account.githubId
        statement[UsersTable.login] = user.account.login
        statement[UsersTable.displayName] = user.profile.name
        statement[UsersTable.avatarUrl] = user.profile.avatarUrl
        statement[UsersTable.lastSignedInAt] = user.lastSignedInAt.atOffset(ZoneOffset.UTC)
    }

    private fun ResultRow.toUser(): User =
        User.restore(
            UserId(this[UsersTable.id]),
            GitHubAccount(this[UsersTable.githubId], this[UsersTable.login]),
            UserProfile(this[UsersTable.displayName], this[UsersTable.avatarUrl]),
            this[UsersTable.lastSignedInAt].toInstant(),
        )
}
