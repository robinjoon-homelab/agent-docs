package xyz.robinjoon.agentdocs.workspace.domain.thread

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MentionsTest {
    @Test
    fun `mentions are lowercase logins outside code and email addresses`() {
        val body = "@Robin please ask @octo-cat. Not me@example.com, not `@Test`, not\n```\n@Inject\n```"

        assertThat(Mentions.of(body)).containsExactly("robin", "octo-cat")
    }
}
