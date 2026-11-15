package xyz.robinjoon.agentdocs.access.domain.emergency

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.access.domain.TokenHash
import xyz.robinjoon.agentdocs.access.domain.user.UserId
import java.time.Instant
import java.util.UUID

class EmergencyLoginGrantTest {
    private val now = Instant.parse("2026-11-16T00:00:00Z")
    private val grant = EmergencyLoginGrant.issue(TokenHash.of("fake-token"), UserId(UUID.randomUUID()), now)

    @Test
    fun `a grant expires at exactly ten minutes and cannot be consumed twice`() {
        assertThat(grant.expiresAt).isEqualTo(now.plusSeconds(600))
        val consumed = grant.consume(now.plusSeconds(599))
        assertThat(consumed.consumed).isTrue()
        assertThat(grant.consumed).isFalse()
        assertThatIllegalArgumentException().isThrownBy { consumed.consume(now.plusSeconds(599)) }
        assertThatIllegalArgumentException().isThrownBy { grant.consume(now.plusSeconds(600)) }
    }
}
