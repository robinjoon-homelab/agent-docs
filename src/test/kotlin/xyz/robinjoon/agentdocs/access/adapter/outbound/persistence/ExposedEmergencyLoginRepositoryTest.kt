package xyz.robinjoon.agentdocs.access.adapter.outbound.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.access.domain.TokenHash
import xyz.robinjoon.agentdocs.access.domain.emergency.EmergencyLoginGrant
import xyz.robinjoon.agentdocs.access.domain.user.GitHubAccount
import xyz.robinjoon.agentdocs.access.domain.user.User
import xyz.robinjoon.agentdocs.access.domain.user.UserId
import xyz.robinjoon.agentdocs.access.domain.user.UserProfile
import xyz.robinjoon.agentdocs.support.persistence.MigratedH2Database
import java.time.Instant
import java.util.UUID

class ExposedEmergencyLoginRepositoryTest {
    private val database = MigratedH2Database()
    private val repository = ExposedEmergencyLoginRepository()
    private val users = ExposedUserRepository()
    private val now = Instant.parse("2026-11-16T00:00:00Z")

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `migration stores a hash and consuming preserves the owner and expiration`() {
        val owner = User.register(UserId(UUID.randomUUID()), GitHubAccount(42, "Owner"), UserProfile(null, null), now)
        val grant = EmergencyLoginGrant.issue(TokenHash.of("fake-token"), owner.id, now)
        database.inTransaction {
            users.add(owner)
            repository.add(grant)
        }
        assertThat(database.inTransaction { repository.lock(grant.tokenHash) }).isEqualTo(grant)
        database.inTransaction { repository.update(grant.consume(now)) }
        assertThat(database.inTransaction { repository.lock(grant.tokenHash) }).isEqualTo(grant.consume(now))
        assertThat(database.inTransaction { repository.lock(TokenHash.of("unknown")) }).isNull()
        assertThat(database.inTransaction { users.findByLogin("OWNER") }).isEqualTo(owner)
    }
}
