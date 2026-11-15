package xyz.robinjoon.agentdocs.access.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import xyz.robinjoon.agentdocs.access.application.port.output.EmergencyLoginRepository
import xyz.robinjoon.agentdocs.access.domain.TokenHash
import xyz.robinjoon.agentdocs.access.domain.emergency.EmergencyLoginGrant
import xyz.robinjoon.agentdocs.access.domain.user.UserId
import java.time.ZoneOffset

class ExposedEmergencyLoginRepository : EmergencyLoginRepository {
    override fun add(grant: EmergencyLoginGrant) {
        EmergencyLoginGrantsTable.insert {
            it[tokenHash] = grant.tokenHash.value
            it[userId] = grant.userId.value
            it[expiresAt] = grant.expiresAt.atOffset(ZoneOffset.UTC)
            it[consumed] = grant.consumed
        }
    }

    override fun lock(hash: TokenHash): EmergencyLoginGrant? =
        EmergencyLoginGrantsTable.selectAll().where { EmergencyLoginGrantsTable.tokenHash eq hash.value }.forUpdate().singleOrNull()?.let {
            EmergencyLoginGrant.restore(
                TokenHash(it[EmergencyLoginGrantsTable.tokenHash]),
                UserId(it[EmergencyLoginGrantsTable.userId]),
                it[EmergencyLoginGrantsTable.expiresAt].toInstant(),
                it[EmergencyLoginGrantsTable.consumed],
            )
        }

    override fun update(grant: EmergencyLoginGrant) {
        val updated =
            EmergencyLoginGrantsTable.update({ EmergencyLoginGrantsTable.tokenHash eq grant.tokenHash.value }) {
                it[consumed] = grant.consumed
            }
        check(updated == 1) { "Emergency login grant is missing" }
    }
}
