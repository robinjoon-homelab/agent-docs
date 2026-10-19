package xyz.robinjoon.agentdocs.access.domain.user

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.access.domain.InvalidAccessException

class AllowlistTest {
    private val allowlist = Allowlist(" RobinJoon ", setOf("Octocat", " "))

    @Test
    fun `logins compare without case and anyone else is refused`() {
        assertThat(allowlist.roleOf("robinjoon")).isEqualTo(UserRole.OWNER)
        assertThat(allowlist.roleOf("OCTOCAT")).isEqualTo(UserRole.MEMBER)
        assertThat(allowlist.roleOf("stranger")).isNull()
    }

    @Test
    fun `an instance without an owner cannot start`() {
        assertThatThrownBy { Allowlist("  ", emptySet()) }
            .isInstanceOf(InvalidAccessException::class.java)
            .hasMessage("the owner login must be set")
    }
}
