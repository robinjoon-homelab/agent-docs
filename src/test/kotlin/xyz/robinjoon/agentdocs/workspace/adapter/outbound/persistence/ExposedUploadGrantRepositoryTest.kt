package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.support.persistence.MigratedH2Database
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.agent
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.at
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.space
import xyz.robinjoon.agentdocs.workspace.domain.upload.UploadGrant
import xyz.robinjoon.agentdocs.workspace.domain.upload.UploadGrantId
import xyz.robinjoon.agentdocs.workspace.domain.upload.UploadSecretHash
import xyz.robinjoon.agentdocs.workspace.domain.upload.UploadTarget
import java.time.Duration
import java.time.Instant
import java.util.UUID

class ExposedUploadGrantRepositoryTest {
    private val database = MigratedH2Database()
    private val repository = ExposedUploadGrantRepository()
    private val docs = database.inTransaction { ExposedSpaceRepository().add(space("docs")) }

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `a grant is found by its secret's hash, with who asked and for which alt text`() {
        val grant = grant("secret", at)

        val added = database.inTransaction { repository.add(grant) }

        assertThat(added).isEqualTo(grant)
        assertThat(find("secret")).isEqualTo(grant)
        assertThat(find("other")).isNull()
    }

    @Test
    fun `a grant can be looked at by its secret's hash`() {
        val grant = database.inTransaction { repository.add(grant("secret", at)) }

        assertThat(database.inTransaction { repository.findBySecretHash(UploadSecretHash.of("secret")) }).isEqualTo(grant)
        assertThat(database.inTransaction { repository.findBySecretHash(UploadSecretHash.of("other")) }).isNull()
    }

    @Test
    fun `using a grant records when`() {
        val grant = database.inTransaction { repository.add(grant("secret", at)) }
        val used = at.plusSeconds(1)

        database.inTransaction { repository.update(grant.used(used)) }

        assertThat(find("secret")?.term?.usedAt).isEqualTo(used)
    }

    @Test
    fun `only grants past their time are forgotten`() {
        database.inTransaction { repository.add(grant("old", at)) }
        database.inTransaction { repository.add(grant("new", at.plus(Duration.ofMinutes(20)))) }

        database.inTransaction { repository.deleteExpiredBefore(at.plus(Duration.ofMinutes(15))) }

        assertThat(find("old")).isNull()
        assertThat(find("new")).isNotNull()
    }

    private fun find(secret: String): UploadGrant? =
        database.inTransaction { repository.findBySecretHashForUpdate(UploadSecretHash.of(secret)) }

    private fun grant(
        secret: String,
        issuedAt: Instant,
    ): UploadGrant =
        UploadGrant.issue(UploadGrantId(UUID.randomUUID()), UploadTarget(docs.id, agent, "흐름 그림"), UploadSecretHash.of(secret), issuedAt)
}
