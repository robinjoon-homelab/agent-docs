package xyz.robinjoon.agentdocs.access.application.port.output

import xyz.robinjoon.agentdocs.access.domain.TokenHash
import xyz.robinjoon.agentdocs.access.domain.emergency.EmergencyLoginGrant

interface EmergencyLoginRepository {
    fun add(grant: EmergencyLoginGrant)

    fun lock(hash: TokenHash): EmergencyLoginGrant?

    fun update(grant: EmergencyLoginGrant)
}
