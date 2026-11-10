package xyz.robinjoon.agentdocs.workspace.application.port.input

import xyz.robinjoon.agentdocs.workspace.application.model.GrantedUpload
import xyz.robinjoon.agentdocs.workspace.application.model.UploadPass
import xyz.robinjoon.agentdocs.workspace.application.model.UploadedFile
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import java.util.UUID

interface UploadGrantUseCase {
    // Lets the calling agent send one file into the space from its shell, instead of writing the file out in a tool call.
    fun grant(
        context: WriteContext,
        space: SpaceSlug,
        alt: String,
    ): UploadPass

    // Whether this connection holds a live pass with this secret, asked before the request counts for anything. A yes
    // only lets the request in; upload decides again under a lock.
    fun admits(
        connection: UUID,
        secret: String,
    ): Boolean

    // Keeps a file sent with a pass. Fails with UploadRejectedException when the secret is unknown, expired, used, or
    // brought by another connection. A file that cannot be kept leaves the pass good for another try.
    fun upload(
        context: WriteContext,
        secret: String,
        file: UploadedFile,
    ): GrantedUpload

    fun forgetExpired()
}
