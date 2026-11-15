package xyz.robinjoon.agentdocs.access.adapter.inbound.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.access.application.model.AccessSettings

class AdocsClientMetadataControllerTest {
    @Test
    fun `metadata identifies a public first party client with loopback callback`() {
        val controller = AdocsClientMetadataController(AccessSettings("owner", emptySet(), "https://docs.example"))
        val metadata = controller.metadata()
        assertThat(metadata["client_id"]).isEqualTo("https://docs.example/oauth/clients/adocs")
        assertThat(metadata["redirect_uris"]).isEqualTo(listOf("http://127.0.0.1/callback"))
        assertThat(metadata["token_endpoint_auth_method"]).isEqualTo("none")
        assertThat(metadata["grant_types"]).isEqualTo(listOf("authorization_code", "refresh_token"))
    }
}
