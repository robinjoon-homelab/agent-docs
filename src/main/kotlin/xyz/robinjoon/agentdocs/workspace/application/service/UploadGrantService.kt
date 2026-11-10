package xyz.robinjoon.agentdocs.workspace.application.service

import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.workspace.application.model.GrantedUpload
import xyz.robinjoon.agentdocs.workspace.application.model.UploadPass
import xyz.robinjoon.agentdocs.workspace.application.model.UploadedFile
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.application.port.input.AssetUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.SpaceNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.input.UploadGrantUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.UploadRejectedException
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.UploadGrantRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.UploadSecretSource
import xyz.robinjoon.agentdocs.workspace.domain.Permission
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import xyz.robinjoon.agentdocs.workspace.domain.upload.UploadGrant
import xyz.robinjoon.agentdocs.workspace.domain.upload.UploadGrantId
import xyz.robinjoon.agentdocs.workspace.domain.upload.UploadSecretHash
import xyz.robinjoon.agentdocs.workspace.domain.upload.UploadTarget
import java.time.Clock
import java.util.UUID

// The asset and the used grant commit together, so a file is taken once and a failed one leaves the grant good.
@Transactional(readOnly = true)
class UploadGrantService(
    private val spaces: SpaceRepository,
    private val grants: UploadGrantRepository,
    private val secrets: UploadSecretSource,
    private val assets: AssetUseCase,
    private val clock: Clock,
) : UploadGrantUseCase {
    @Transactional
    override fun grant(
        context: WriteContext,
        space: SpaceSlug,
        alt: String,
    ): UploadPass {
        val target = spaces.findBySlug(space)?.takeIf { context.access.sees(it.id) } ?: throw SpaceNotFoundException(space)
        Permissions.require(context.access, target.id, Permission.EDIT, "uploading files")
        val secret = secrets.next()
        val grant =
            UploadGrant.issue(
                UploadGrantId(UUID.randomUUID()),
                UploadTarget(target.id, context.access.actor, alt),
                UploadSecretHash.of(secret),
                clock.instant(),
            )
        return grants.add(grant).let { UploadPass(it.target.connectionId, secret, it.term.expiresAt) }
    }

    override fun admits(
        connection: UUID,
        secret: String,
    ): Boolean = grants.findBySecretHash(UploadSecretHash.of(secret))?.isOpenTo(connection, clock.instant()) == true

    @Transactional
    override fun upload(
        context: WriteContext,
        secret: String,
        file: UploadedFile,
    ): GrantedUpload {
        val now = clock.instant()
        val grant =
            grants.findBySecretHashForUpdate(UploadSecretHash.of(secret))?.takeIf { it.acceptsFrom(context.access.actor, now) }
                ?: throw UploadRejectedException()
        val space = spaces.findById(grant.target.spaceId) ?: throw UploadRejectedException()
        val asset = assets.upload(context, space.slug, file)
        grants.update(grant.used(now))
        return GrantedUpload(asset, grant.target.alt)
    }

    @Transactional
    override fun forgetExpired() {
        grants.deleteExpiredBefore(clock.instant())
    }
}
