package xyz.robinjoon.agentdocs.access.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.access.application.model.UploadToken
import java.util.UUID

class UploadTokenTest {
    private val connection = UUID.fromString("22222222-2222-4222-8222-222222222222")

    @Test
    fun `a token names its connection and carries its secret`() {
        val token = UploadToken.of(connection, "s3cr.et_-x")

        assertThat(token).startsWith("adt_u_")
        assertThat(UploadToken.connectionOf(token)).isEqualTo(connection)
        assertThat(UploadToken.secretOf(token)).isEqualTo("s3cr.et_-x")
    }

    @Test
    fun `anything else is not an upload token`() {
        listOf("adt_a_token", "adt_u_", "adt_u_not-a-uuid.secret", "adt_u_$connection", "adt_u_$connection.").forEach {
            assertThat(UploadToken.connectionOf(it)).isNull()
            assertThat(UploadToken.secretOf(it)).isNull()
        }
    }
}
