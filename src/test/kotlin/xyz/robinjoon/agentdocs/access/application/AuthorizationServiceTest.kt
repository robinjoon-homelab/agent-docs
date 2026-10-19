package xyz.robinjoon.agentdocs.access.application

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.CLAUDE_CODE
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.agentCaller
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.claudeCode
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.clock
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.now
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.owner
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.settings
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.space
import xyz.robinjoon.agentdocs.access.application.model.ApprovalRequest
import xyz.robinjoon.agentdocs.access.application.model.AuthorizationStart
import xyz.robinjoon.agentdocs.access.application.model.AuthorizeRequest
import xyz.robinjoon.agentdocs.access.application.model.ClientMetadataDocument
import xyz.robinjoon.agentdocs.access.application.model.PkceParameters
import xyz.robinjoon.agentdocs.access.application.model.TargetParameters
import xyz.robinjoon.agentdocs.access.application.port.input.AuthorizationRequestNotFoundException
import xyz.robinjoon.agentdocs.access.application.port.input.OwnerOnlyException
import xyz.robinjoon.agentdocs.access.application.port.output.AuthorizationRequestRepository
import xyz.robinjoon.agentdocs.access.application.port.output.ClientMetadataSource
import xyz.robinjoon.agentdocs.access.application.port.output.OAuthClientRepository
import xyz.robinjoon.agentdocs.access.application.port.output.SecretSource
import xyz.robinjoon.agentdocs.access.application.service.AccessJournal
import xyz.robinjoon.agentdocs.access.application.service.AuthorizationService
import xyz.robinjoon.agentdocs.access.application.service.ClientDirectory
import xyz.robinjoon.agentdocs.access.domain.authorization.AuthorizationAsk
import xyz.robinjoon.agentdocs.access.domain.authorization.AuthorizationDecision
import xyz.robinjoon.agentdocs.access.domain.authorization.AuthorizationRequest
import xyz.robinjoon.agentdocs.access.domain.authorization.AuthorizationRequestId
import xyz.robinjoon.agentdocs.access.domain.authorization.PkceChallenge
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionScope
import xyz.robinjoon.agentdocs.application.port.output.ActivityReporter
import java.util.UUID

class AuthorizationServiceTest {
    private val requests = mockk<AuthorizationRequestRepository>()
    private val clients = mockk<OAuthClientRepository>()
    private val documents = mockk<ClientMetadataSource>()
    private val secrets = mockk<SecretSource>()
    private val activity = mockk<ActivityReporter>()
    private val directory = ClientDirectory(clients, documents, secrets, clock)
    private val service = AuthorizationService(requests, clients, directory, secrets, AccessJournal(activity, clock), settings)
    private val challenge = "MpKhuRql5U_ItQvQquj4AyZQ-hTxEsV8TScgj9SGVsY"

    init {
        every { documents.fetch(CLAUDE_CODE) } returns
            ClientMetadataDocument(CLAUDE_CODE, "Claude Code", listOf("http://localhost/callback"), "none")
        every { clients.find(CLAUDE_CODE) } returns claudeCode
        every { clients.save(any()) } answers { firstArg() }
        every { activity.record(any()) } just runs
    }

    @Test
    fun `a valid request waits for the owner on the approval screen`() {
        every { requests.add(any()) } answers { firstArg() }

        val start = service.begin(request(redirect = "http://localhost:50111/callback"))

        assertThat(start).isInstanceOf(AuthorizationStart.Started::class.java)
    }

    @Test
    fun `an unregistered redirect is shown as an error page, never followed`() {
        val start = service.begin(request(redirect = "https://evil.example/callback"))

        assertThat(start).isEqualTo(AuthorizationStart.Rejected("redirect_uri is not registered for this client"))
    }

    @Test
    fun `missing PKCE and a foreign resource go back to the client with an error, the state, and the issuer`() {
        val noPkce = service.begin(request(method = "plain"))
        val foreign = service.begin(request(resource = "https://other.example/mcp"))

        assertThat((noPkce as AuthorizationStart.RedirectWithError).location)
            .startsWith("http://localhost:50111/callback?error=invalid_request&error_description=PKCE")
            .endsWith("&state=xyz&iss=https%3A%2F%2Fdocs.example.com")
        assertThat((foreign as AuthorizationStart.RedirectWithError).location).contains("error=invalid_target")
    }

    @Test
    fun `approving returns the redirect with a one-time code and only the owner may approve`() {
        val pending = pending()
        every { requests.findByIdForUpdate(pending.id) } returns pending
        every { requests.update(any()) } answers { firstArg() }
        every { secrets.next() } returns "secret"

        val location = service.approve(owner, pending.id, ApprovalRequest(setOf(space), ConnectionScope.EDIT, 90))

        assertThat(location).isEqualTo("http://localhost:50111/callback?code=adt_c_secret&state=xyz&iss=https%3A%2F%2Fdocs.example.com")
        assertThatThrownBy { service.approve(agentCaller, pending.id, ApprovalRequest(setOf(space), ConnectionScope.EDIT, 90)) }
            .isInstanceOf(OwnerOnlyException::class.java)
    }

    @Test
    fun `the approval screen shows a waiting request with its client and refuses an expired one`() {
        val pending = pending()
        val expired =
            AuthorizationRequest.restore(
                AuthorizationRequestId(UUID.randomUUID()),
                pending.ask,
                AuthorizationDecision.Pending,
                now,
            )
        every { requests.findById(pending.id) } returns pending
        every { requests.findById(expired.id) } returns expired

        val view = service.describe(owner, pending.id)

        assertThat(view.client).isEqualTo(claudeCode)
        assertThat(view.request.id).isEqualTo(pending.id)
        assertThatThrownBy { service.describe(owner, expired.id) }.isInstanceOf(AuthorizationRequestNotFoundException::class.java)
    }

    @Test
    fun `denying sends access_denied back to the client`() {
        val pending = pending()
        every { requests.findByIdForUpdate(pending.id) } returns pending
        every { requests.update(any()) } answers { firstArg() }

        assertThat(service.deny(owner, pending.id)).contains("error=access_denied").contains("state=xyz")
    }

    private fun pending(): AuthorizationRequest {
        val ask = AuthorizationAsk(CLAUDE_CODE, "http://localhost:50111/callback", "xyz", PkceChallenge(challenge), settings.resource, null)
        return AuthorizationRequest.restore(
            AuthorizationRequestId(UUID.randomUUID()),
            ask,
            AuthorizationDecision.Pending,
            now.plusSeconds(600),
        )
    }

    private fun request(
        redirect: String = "http://localhost:50111/callback",
        method: String = "S256",
        resource: String = "https://docs.example.com/mcp",
    ): AuthorizeRequest =
        AuthorizeRequest(
            "code",
            CLAUDE_CODE,
            redirect,
            "xyz",
            PkceParameters(challenge, method),
            TargetParameters("read edit", listOf(resource)),
        )
}
