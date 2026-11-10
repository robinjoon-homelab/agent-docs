package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import xyz.robinjoon.agentdocs.workspace.application.port.output.UploadGrantRepository
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.upload.UploadGrant
import xyz.robinjoon.agentdocs.workspace.domain.upload.UploadGrantId
import xyz.robinjoon.agentdocs.workspace.domain.upload.UploadSecretHash
import xyz.robinjoon.agentdocs.workspace.domain.upload.UploadTarget
import xyz.robinjoon.agentdocs.workspace.domain.upload.UploadTerm
import java.time.Instant

class ExposedUploadGrantRepository : UploadGrantRepository {
    override fun add(grant: UploadGrant): UploadGrant {
        UploadGrantsTable.insert {
            it[id] = grant.id.value
            it[spaceId] = grant.target.spaceId.value
            issuer.write(it, grant.target.issuer)
            it[alt] = grant.target.alt
            it[secretHash] = grant.secretHash.value
            it[expiresAt] = grant.term.expiresAt.utc()
            it[usedAt] = grant.term.usedAt?.utc()
        }
        return grant
    }

    // Only its use changes; what it allows stays as it was given.
    override fun update(grant: UploadGrant): UploadGrant {
        val updated = UploadGrantsTable.update({ UploadGrantsTable.id eq grant.id.value }) { it[usedAt] = grant.term.usedAt?.utc() }
        check(updated == 1) { "Upload grant to update does not exist: ${grant.id}" }
        return grant
    }

    override fun findBySecretHash(hash: UploadSecretHash): UploadGrant? = bySecretHash(hash).singleOrNull()?.toUploadGrant()

    override fun findBySecretHashForUpdate(hash: UploadSecretHash): UploadGrant? =
        bySecretHash(hash).forUpdate().singleOrNull()?.toUploadGrant()

    private fun bySecretHash(hash: UploadSecretHash) = UploadGrantsTable.selectAll().where { UploadGrantsTable.secretHash eq hash.value }

    override fun deleteExpiredBefore(cutoff: Instant) {
        UploadGrantsTable.deleteWhere { expiresAt less cutoff.utc() }
    }

    private fun ResultRow.toUploadGrant(): UploadGrant =
        UploadGrant.restore(
            UploadGrantId(this[UploadGrantsTable.id]),
            UploadTarget(SpaceId(this[UploadGrantsTable.spaceId]), UploadGrantsTable.issuer.read(this), this[UploadGrantsTable.alt]),
            UploadSecretHash(this[UploadGrantsTable.secretHash]),
            UploadTerm(this[UploadGrantsTable.expiresAt].toInstant(), this[UploadGrantsTable.usedAt]?.toInstant()),
        )
}
