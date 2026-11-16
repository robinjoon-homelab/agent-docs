package xyz.robinjoon.agentdocs.access.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class GitHubLoginTest {
    @Test
    fun `a login keeps its spelling and compares by a lowercase key`() {
        val login = GitHubLogin("Octo-Cat")

        assertThat(login.value).isEqualTo("Octo-Cat")
        assertThat(login.key).isEqualTo(GitHubLogin("octo-cat").key)
    }

    @Test
    fun `a login GitHub could not have is refused`() {
        listOf("", "-octocat", "octo cat", "octo_cat", "a".repeat(40)).forEach { value ->
            assertThatThrownBy { GitHubLogin(value) }.isInstanceOf(InvalidAccessException::class.java)
        }
        assertThat(GitHubLogin("a".repeat(39)).value).hasSize(39)
    }

    @Test
    fun `a new invitation takes only single inner hyphens while stored logins keep the lenient shape`() {
        listOf("Octo-Cat", "a", "a1-B2-c3", "A".repeat(39)).forEach { value ->
            assertThat(GitHubLogin.isValidInvitation(value)).`as`(value).isTrue()
        }
        listOf("", "-octocat", "octocat-", "octo--cat", "octo_cat", "octo cat", "a".repeat(40)).forEach { value ->
            assertThat(GitHubLogin.isValidInvitation(value)).`as`(value).isFalse()
        }
        listOf("octocat-", "octo--cat").forEach { legacy ->
            assertThat(GitHubLogin.isValid(legacy)).`as`(legacy).isTrue()
            assertThat(GitHubLogin(legacy).value).isEqualTo(legacy)
        }
    }
}
