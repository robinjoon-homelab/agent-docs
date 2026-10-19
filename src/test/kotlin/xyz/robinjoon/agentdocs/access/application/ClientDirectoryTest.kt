package xyz.robinjoon.agentdocs.access.application

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.CLAUDE_CODE
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.clock
import xyz.robinjoon.agentdocs.access.application.model.ClientMetadataDocument
import xyz.robinjoon.agentdocs.access.application.model.ClientRegistration
import xyz.robinjoon.agentdocs.access.application.model.OAuthException
import xyz.robinjoon.agentdocs.access.application.port.output.ClientMetadataSource
import xyz.robinjoon.agentdocs.access.application.port.output.OAuthClientRepository
import xyz.robinjoon.agentdocs.access.application.port.output.SecretSource
import xyz.robinjoon.agentdocs.access.application.service.ClientDirectory
import xyz.robinjoon.agentdocs.access.domain.client.ClientKind

class ClientDirectoryTest {
    private val clients = mockk<OAuthClientRepository>()
    private val documents = mockk<ClientMetadataSource>()
    private val secrets = mockk<SecretSource>()
    private val directory = ClientDirectory(clients, documents, secrets, clock)

    @Test
    fun `a metadata document client is trusted only when the document names its own URL`() {
        every { documents.fetch(CLAUDE_CODE) } returns
            ClientMetadataDocument(CLAUDE_CODE, "Claude Code", listOf("http://localhost/callback"), "none")
        every { clients.find(CLAUDE_CODE) } returns null
        every { clients.save(any()) } answers { firstArg() }

        val client = directory.resolve(CLAUDE_CODE)

        assertThat(client.kind).isEqualTo(ClientKind.METADATA_DOCUMENT)
        assertThat(client.host).isEqualTo("claude.ai")
        assertThat(client.allowsRedirect("http://localhost:61234/callback")).isTrue()
    }

    @Test
    fun `a document for another client id or a confidential client is refused`() {
        every { documents.fetch(CLAUDE_CODE) } returns
            ClientMetadataDocument("https://evil.example/client", "Claude Code", listOf("http://localhost/callback"), null)
        every { documents.fetch("https://example.com/client.json") } returns
            ClientMetadataDocument("https://example.com/client.json", null, listOf("https://example.com/cb"), "client_secret_basic")

        assertThatThrownBy { directory.resolve(CLAUDE_CODE) }
            .isInstanceOfSatisfying(OAuthException::class.java) { assertThat(it.error).isEqualTo("invalid_client") }
        assertThatThrownBy { directory.resolve("https://example.com/client.json") }
            .isInstanceOfSatisfying(OAuthException::class.java) { assertThat(it.error).isEqualTo("invalid_client_metadata") }
        assertThatThrownBy { directory.resolve("https://example.com/") }
            .isInstanceOfSatisfying(OAuthException::class.java) { assertThat(it.error).isEqualTo("invalid_client") }
    }

    @Test
    fun `dynamic registration accepts only public clients with valid redirect URIs`() {
        every { secrets.next() } returns "generated"
        every { clients.save(any()) } answers { firstArg() }

        val registered =
            directory.register(
                ClientRegistration("Tool", listOf("http://127.0.0.1/callback"), "none", listOf("authorization_code")),
            )

        assertThat(registered.clientId).isEqualTo("adc_generated")
        assertThat(registered.kind).isEqualTo(ClientKind.REGISTERED)
        assertThatThrownBy { directory.register(ClientRegistration("Tool", listOf("http://example.com/cb"), null, null)) }
            .isInstanceOfSatisfying(OAuthException::class.java) { assertThat(it.error).isEqualTo("invalid_redirect_uri") }
        assertThatThrownBy {
            directory.register(
                ClientRegistration("Tool", listOf("http://127.0.0.1/cb"), null, listOf("client_credentials")),
            )
        }.isInstanceOfSatisfying(OAuthException::class.java) { assertThat(it.error).isEqualTo("invalid_client_metadata") }
    }
}
