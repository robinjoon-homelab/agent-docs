package xyz.robinjoon.agentdocs.access.domain.session

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.access.domain.TokenHash
import xyz.robinjoon.agentdocs.access.domain.user.UserId
import java.time.Duration
import java.time.Instant
import java.util.UUID

class WebSessionTest {
    private val start = Instant.parse("2026-10-18T09:00:00Z")
    private val session = WebSession.start(WebSessionId(UUID.randomUUID()), UserId(UUID.randomUUID()), TokenHash.of("adt_w_x"), start)

    @Test
    fun `a sign-in ends after 30 days without use`() {
        assertThat(session.acceptsAt(start.plus(Duration.ofDays(30)).minusSeconds(1))).isTrue()
        assertThat(session.acceptsAt(start.plus(Duration.ofDays(30)))).isFalse()
    }

    @Test
    fun `use pushes the end 30 days past the latest use, recorded at most once a day`() {
        val sameDay = start.plus(Duration.ofHours(23))
        val nextDay = start.plus(Duration.ofDays(1))

        assertThat(session.used(sameDay)).isNull()
        val renewed = requireNotNull(session.used(nextDay))
        assertThat(renewed.term.lastUsedAt).isEqualTo(nextDay)
        assertThat(renewed.acceptsAt(nextDay.plus(Duration.ofDays(30)).minusSeconds(1))).isTrue()
        assertThat(renewed.acceptsAt(nextDay.plus(Duration.ofDays(30)))).isFalse()
    }
}
