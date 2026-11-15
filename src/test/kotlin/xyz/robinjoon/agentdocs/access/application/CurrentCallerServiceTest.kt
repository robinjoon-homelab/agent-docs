package xyz.robinjoon.agentdocs.access.application

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.access.application.model.Caller
import xyz.robinjoon.agentdocs.access.application.model.CallerScope
import xyz.robinjoon.agentdocs.access.application.port.input.CallerUseCase
import xyz.robinjoon.agentdocs.access.application.port.input.WebSessionUseCase
import xyz.robinjoon.agentdocs.access.application.service.CurrentCallerService
import xyz.robinjoon.agentdocs.application.model.Credential
import xyz.robinjoon.agentdocs.application.model.CurrentCallerSnapshot
import java.util.UUID

class CurrentCallerServiceTest {
    private val callers = mockk<CallerUseCase>()
    private val sessions = mockk<WebSessionUseCase>()
    private val service = CurrentCallerService(callers, sessions)
    private val caller = Caller(UUID.randomUUID(), "owner", true, null, null, CallerScope.OWNER)

    @Test
    fun `a previously valid bearer becomes invalid after revocation`() {
        val credential = Credential(Credential.Kind.BEARER, "test-token")
        every { callers.callerFor("test-token") } returns caller
        assertThat(service.current(credential)?.scope).isEqualTo(CurrentCallerSnapshot.Scope.OWNER)
        every { callers.callerFor("test-token") } returns null
        assertThat(service.current(credential)).isNull()
        assertThat(credential.toString()).doesNotContain("test-token")
    }

    @Test
    fun `browser snapshots reflect the current session and expiry`() {
        val credential = Credential(Credential.Kind.WEB_SESSION, "test-session")
        every { sessions.currentCaller("test-session") } returns caller
        assertThat(service.current(credential)?.login).isEqualTo("owner")
        every { sessions.currentCaller("test-session") } returns null
        assertThat(service.current(credential)).isNull()
    }
}
