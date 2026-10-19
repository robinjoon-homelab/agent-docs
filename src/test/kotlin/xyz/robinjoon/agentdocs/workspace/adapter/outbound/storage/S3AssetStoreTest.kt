package xyz.robinjoon.agentdocs.workspace.adapter.outbound.storage

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import software.amazon.awssdk.core.ResponseBytes
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import xyz.robinjoon.agentdocs.workspace.application.model.AssetContentMissingException

class S3AssetStoreTest {
    private val client = mockk<S3Client>()
    private val store = S3AssetStore(client, "homelab", "agent-docs/")

    @Test
    fun `bytes are read from the hash under the prefix and a missing key is reported as missing content`() {
        val stored =
            GetObjectRequest
                .builder()
                .bucket("homelab")
                .key("agent-docs/abc")
                .build()
        val missing =
            GetObjectRequest
                .builder()
                .bucket("homelab")
                .key("agent-docs/def")
                .build()
        every { client.getObjectAsBytes(stored) } returns
            ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), byteArrayOf(1, 2))
        every { client.getObjectAsBytes(missing) } throws NoSuchKeyException.builder().message("missing").build()

        assertThat(store.get("abc")).isEqualTo(byteArrayOf(1, 2))
        assertThatThrownBy { store.get("def") }.isInstanceOf(AssetContentMissingException::class.java)
    }

    private fun request(key: String): GetObjectRequest =
        GetObjectRequest
            .builder()
            .bucket("homelab")
            .key(key)
            .build()
}
