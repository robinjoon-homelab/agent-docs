package xyz.robinjoon.agentdocs.workspace.application.service

import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.workspace.application.port.input.ExportUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.PermissionDeniedException
import xyz.robinjoon.agentdocs.workspace.application.port.output.ExportArchive
import xyz.robinjoon.agentdocs.workspace.application.port.output.ExportCatalog
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.Permission
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug

@Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
class ExportService(
    private val catalog: ExportCatalog,
    private val archive: ExportArchive,
) : ExportUseCase {
    override fun export(
        access: Access,
        space: SpaceSlug?,
    ): ByteArray {
        if (!access.isOwner || access.actor.isAgent) throw PermissionDeniedException(Permission.OWNER, "exporting workspace data")
        return try {
            archive.zip(catalog.snapshot(space))
        } catch (unavailable: xyz.robinjoon.agentdocs.workspace.application.model.ExportSpaceUnavailableException) {
            throw xyz.robinjoon.agentdocs.workspace.application.port.input
                .SpaceNotFoundException(unavailable.space, unavailable)
        }
    }
}
