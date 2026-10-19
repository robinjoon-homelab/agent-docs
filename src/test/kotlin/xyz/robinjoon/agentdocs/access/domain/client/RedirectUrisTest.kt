package xyz.robinjoon.agentdocs.access.domain.client

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import xyz.robinjoon.agentdocs.access.domain.InvalidAccessException

class RedirectUrisTest {
    @ParameterizedTest
    @CsvSource(
        "http://localhost/callback, http://localhost:53682/callback, true",
        "http://127.0.0.1/callback, http://127.0.0.1:1455/callback, true",
        "http://127.0.0.1/callback, http://127.0.0.1:1455/callback/other, false",
        "http://localhost/callback, http://127.0.0.1:1455/callback, false",
        "https://claude.ai/api/mcp/auth_callback, https://claude.ai/api/mcp/auth_callback, true",
        "https://claude.ai/api/mcp/auth_callback, https://claude.ai:444/api/mcp/auth_callback, false",
    )
    fun `redirects match exactly except for the port of a loopback URI`(
        registered: String,
        requested: String,
        matches: Boolean,
    ) {
        assertThat(RedirectUris.matches(registered, requested)).isEqualTo(matches)
    }

    @ParameterizedTest
    @ValueSource(strings = ["http://example.com/callback", "https://example.com/cb#fragment", "javascript:alert(1)", "not a uri"])
    fun `redirects must be https or loopback http without a fragment`(uri: String) {
        assertThatThrownBy { RedirectUris.requireValid(uri) }.isInstanceOf(InvalidAccessException::class.java)
    }
}
