package xyz.robinjoon.agentdocs.workspace.adapter.outbound.storage

import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import xyz.robinjoon.agentdocs.workspace.application.model.AssetContentMissingException
import xyz.robinjoon.agentdocs.workspace.application.port.output.AssetStore

// The homelab's shared S3-compatible object storage. Objects are keyed by content hash under a prefix, so the same
// bytes are stored once and never change.
class S3AssetStore(
    private val client: S3Client,
    private val bucket: String,
    private val prefix: String,
) : AssetStore {
    override fun put(
        sha256: String,
        mediaType: String,
        bytes: ByteArray,
    ) {
        val request =
            PutObjectRequest
                .builder()
                .bucket(bucket)
                .key(keyOf(sha256))
                .contentType(mediaType)
                .build()
        client.putObject(request, RequestBody.fromBytes(bytes))
    }

    override fun get(sha256: String): ByteArray {
        val request =
            GetObjectRequest
                .builder()
                .bucket(bucket)
                .key(keyOf(sha256))
                .build()
        return try {
            client.getObjectAsBytes(request).asByteArray()
        } catch (exception: NoSuchKeyException) {
            throw AssetContentMissingException(sha256, exception)
        }
    }

    private fun keyOf(sha256: String): String = "${prefix.trimEnd('/')}/$sha256".trimStart('/')
}
