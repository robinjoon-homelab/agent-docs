package xyz.robinjoon.agentdocs.access.application

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.agentCaller
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.clock
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.connection
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.now
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.owner
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.space
import xyz.robinjoon.agentdocs.access.application.model.GrantChange
import xyz.robinjoon.agentdocs.access.application.model.ScriptTokenRequest
import xyz.robinjoon.agentdocs.access.application.port.input.ConnectionNotFoundException
import xyz.robinjoon.agentdocs.access.application.port.input.OwnerOnlyException
import xyz.robinjoon.agentdocs.access.application.port.output.ConnectionRepository
import xyz.robinjoon.agentdocs.access.application.port.output.SecretSource
import xyz.robinjoon.agentdocs.access.application.service.AccessJournal
import xyz.robinjoon.agentdocs.access.application.service.ConnectionService
import xyz.robinjoon.agentdocs.access.domain.InvalidAccessException
import xyz.robinjoon.agentdocs.access.domain.TokenHash
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionId
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionKind
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionScope
import xyz.robinjoon.agentdocs.application.port.output.ActivityReporter
import java.time.Duration
import java.util.UUID

class ConnectionServiceTest {
    private val connections = mockk<ConnectionRepository>()
    private val secrets = mockk<SecretSource>()
    private val activity = mockk<ActivityReporter>()
    private val service = ConnectionService(connections, secrets, AccessJournal(activity, clock))

    init {
        every { activity.record(any()) } just runs
    }

    @Test
    fun `a script token is shown once and stored only as its hash for the whole lifetime`() {
        every { secrets.next() } returns "secret"
        every { connections.add(any()) } answers { firstArg() }

        val issued = service.issue(owner, ScriptTokenRequest(" backup script ", setOf(space), ConnectionScope.READ, 30))

        assertThat(issued.token).isEqualTo("adt_s_secret")
        assertThat(issued.connection.credentials.accessHash).isEqualTo(TokenHash.of("adt_s_secret"))
        assertThat(issued.connection.credentials.refreshHash).isNull()
        assertThat(issued.connection.credentials.accessExpiresAt).isEqualTo(now.plus(Duration.ofDays(30)))
        assertThat(issued.connection.owner.kind).isEqualTo(ConnectionKind.SCRIPT)
        assertThat(issued.connection.owner.client.name).isEqualTo("backup script")
    }

    @Test
    fun `only the owner manages connections, never an agent`() {
        assertThatThrownBy { service.list(agentCaller) }.isInstanceOf(OwnerOnlyException::class.java)
        assertThatThrownBy {
            service.issue(agentCaller, ScriptTokenRequest("x", emptySet(), ConnectionScope.READ, 1))
        }.isInstanceOf(OwnerOnlyException::class.java)
    }

    @Test
    fun `revoking twice keeps the first revocation and narrowing replaces the grant`() {
        val active = connection("adt_a_t", "adt_r_t")
        every { connections.findById(active.id) } returns active
        every { connections.update(any()) } answers { firstArg() }

        val revoked = service.revoke(owner, active.id)
        every { connections.findById(active.id) } returns revoked
        val again = service.revoke(owner, active.id)
        val narrowed = service.regrant(owner, active.id, GrantChange(emptySet(), ConnectionScope.READ))

        assertThat(revoked.term.revokedAt).isEqualTo(now)
        assertThat(again).isEqualTo(revoked)
        assertThat(narrowed.grant.spaces).isEmpty()
        assertThat(narrowed.grant.scope).isEqualTo(ConnectionScope.READ)
    }

    @Test
    fun `an unknown connection and a lifetime outside a year are refused`() {
        val missing = ConnectionId(UUID.randomUUID())
        every { connections.findById(missing) } returns null

        assertThatThrownBy { service.revoke(owner, missing) }.isInstanceOf(ConnectionNotFoundException::class.java)
        assertThatThrownBy { service.issue(owner, ScriptTokenRequest("x", emptySet(), ConnectionScope.READ, 366)) }
            .isInstanceOf(InvalidAccessException::class.java)
            .hasMessage("a connection lasts 1 to 365 days")
    }
}
