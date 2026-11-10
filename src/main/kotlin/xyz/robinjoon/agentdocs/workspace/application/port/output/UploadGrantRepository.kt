package xyz.robinjoon.agentdocs.workspace.application.port.output

import xyz.robinjoon.agentdocs.workspace.domain.upload.UploadGrant
import xyz.robinjoon.agentdocs.workspace.domain.upload.UploadSecretHash
import java.time.Instant

interface UploadGrantRepository {
    fun add(grant: UploadGrant): UploadGrant

    fun update(grant: UploadGrant): UploadGrant

    fun findBySecretHash(hash: UploadSecretHash): UploadGrant?

    // Locks the row, so two requests with one secret take turns and the second finds the grant used.
    fun findBySecretHashForUpdate(hash: UploadSecretHash): UploadGrant?

    fun deleteExpiredBefore(cutoff: Instant)
}
