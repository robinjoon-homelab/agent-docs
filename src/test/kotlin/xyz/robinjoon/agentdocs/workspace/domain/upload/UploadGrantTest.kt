package xyz.robinjoon.agentdocs.workspace.domain.upload

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.domain.Actor
import xyz.robinjoon.agentdocs.workspace.domain.AgentIdentity
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.asset.InvalidAssetException
import java.time.Duration
import java.time.Instant
import java.util.UUID

class UploadGrantTest {
    private val at = Instant.parse("2026-10-18T09:00:00Z")
    private val user = UUID.fromString("11111111-1111-4111-8111-111111111111")
    private val connection = UUID.fromString("22222222-2222-4222-8222-222222222222")
    private val agent = Actor.agent(user, "robin", AgentIdentity(connection, "Claude Code"))
    private val space = SpaceId(UUID.fromString("33333333-3333-4333-8333-333333333333"))
    private val grant =
        UploadGrant.issue(UploadGrantId(UUID.randomUUID()), UploadTarget(space, agent, "흐름"), UploadSecretHash.of("secret"), at)

    @Test
    fun `a grant takes a file from the connection it was given to for ten minutes`() {
        assertThat(grant.term.expiresAt).isEqualTo(at.plus(Duration.ofMinutes(10)))
        assertThat(grant.target.connectionId).isEqualTo(connection)
        assertThat(grant.acceptsFrom(agent, at.plus(Duration.ofMinutes(10)).minusNanos(1))).isTrue()
    }

    @Test
    fun `a grant past its time takes nothing`() {
        assertThat(grant.acceptsFrom(agent, grant.term.expiresAt)).isFalse()
    }

    @Test
    fun `a used grant takes nothing more`() {
        val used = grant.used(at.plusSeconds(1))

        assertThat(used.term.usedAt).isEqualTo(at.plusSeconds(1))
        assertThat(used.acceptsFrom(agent, at.plusSeconds(2))).isFalse()
    }

    @Test
    fun `a grant takes nothing from another connection of the same person`() {
        val other = Actor.agent(user, "robin", AgentIdentity(UUID.randomUUID(), "Codex"))

        assertThat(grant.acceptsFrom(other, at)).isFalse()
    }

    @Test
    fun `a grant is open to its connection until it is used or runs out`() {
        assertThat(grant.isOpenTo(connection, grant.term.expiresAt.minusNanos(1))).isTrue()
        assertThat(grant.isOpenTo(connection, grant.term.expiresAt)).isFalse()
        assertThat(grant.used(at).isOpenTo(connection, at)).isFalse()
    }

    @Test
    fun `a grant is closed to every other connection`() {
        assertThat(grant.isOpenTo(UUID.randomUUID(), at)).isFalse()
    }

    @Test
    fun `only an agent is given a grant, since a person has the web editor`() {
        assertThatThrownBy { UploadTarget(space, Actor.human(user, "robin"), "흐름") }.isInstanceOf(InvalidAssetException::class.java)
    }

    @Test
    fun `only the secret's hash is kept`() {
        val hash = UploadSecretHash.of("secret")

        assertThat(hash.value).hasSize(64).doesNotContain("secret")
        assertThat(hash).isEqualTo(UploadSecretHash.of("secret")).isNotEqualTo(UploadSecretHash.of("other"))
    }
}
