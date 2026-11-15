package xyz.robinjoon.agentdocs.workspace.domain.thread

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.domain.Actor
import xyz.robinjoon.agentdocs.workspace.domain.AgentIdentity
import java.time.Instant
import java.util.UUID

class ThreadCollaborationTest {
    private val at = Instant.parse("2026-11-16T01:00:00Z")
    private val owner = Actor.human(UUID.randomUUID(), "owner")
    private val first = Actor.agent(owner.userId, owner.login, AgentIdentity(UUID.randomUUID(), "Codex"))
    private val second = Actor.agent(owner.userId, owner.login, AgentIdentity(UUID.randomUUID(), "Claude"))

    @Test
    fun `claim is exclusive until exactly fifteen minutes and humans remain free`() {
        val claimed = ThreadCollaboration.empty().claim(first, at, "acquire")
        assertThat(claimed.claim?.expiresAt).isEqualTo(at.plusSeconds(900))
        assertThatThrownBy { claimed.requireAvailable(second, at.plusSeconds(899)) }.isInstanceOf(ThreadActionException::class.java)
        claimed.requireAvailable(owner, at)
        val acquired = claimed.claim(second, at.plusSeconds(900), "acquire")
        assertThat(acquired.claim?.actor).isEqualTo(second)
        assertThat(acquired.claim?.acquiredAt).isEqualTo(at.plusSeconds(900))
    }

    @Test
    fun `renew and release require the original connection even after expiry`() {
        val claimed = ThreadCollaboration.empty().claim(first, at, "acquire")
        assertThatThrownBy { claimed.claim(second, at.plusSeconds(901), "release") }.isInstanceOf(ThreadActionException::class.java)
        assertThatThrownBy { claimed.claim(owner, at, "acquire") }.isInstanceOf(ThreadActionException::class.java)
        val renewed = claimed.claim(first, at.plusSeconds(60), "renew")
        assertThat(renewed.claim?.acquiredAt).isEqualTo(at)
        assertThat(renewed.claim?.expiresAt).isEqualTo(at.plusSeconds(960))
        assertThat(renewed.claim(first, at.plusSeconds(61), "release").claim).isNull()
    }

    @Test
    fun `reactions are an actor and emoji set with explicit removal`() {
        val initial = ThreadCollaboration.empty().react(first, "👍", true)
        val twice = initial.react(first, "👍", true).react(second, "👍", true)
        assertThat(twice.reactions).hasSize(2)
        assertThat(twice.react(first, "👍", false).reactions.map { it.actor }).containsExactly(second)
        assertThatThrownBy { initial.react(owner, " ", true) }.isInstanceOf(InvalidThreadException::class.java)
    }
}
