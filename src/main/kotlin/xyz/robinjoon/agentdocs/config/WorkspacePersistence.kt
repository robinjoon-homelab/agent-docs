package xyz.robinjoon.agentdocs.config

import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.ExposedAssetRepository
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.ExposedDocumentAssetStore
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.ExposedDocumentEventCatalog
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.ExposedDocumentRepository
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.ExposedEditSessionRepository
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.ExposedExportCatalog
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.ExposedInboxRepository
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.ExposedNavigationCatalog
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.ExposedOperationStore
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.ExposedPersonalDocumentStore
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.ExposedRevisionCatalog
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.ExposedSearchCatalog
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.ExposedSpaceGuideCatalog
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.ExposedSpaceRepository
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.ExposedSpaceStatisticsCatalog
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.ExposedThreadRepository
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.ExposedUploadGrantRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.AssetRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentAssetStore
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentEventCatalog
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.EditSessionRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.ExportCatalog
import xyz.robinjoon.agentdocs.workspace.application.port.output.InboxRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.NavigationCatalog
import xyz.robinjoon.agentdocs.workspace.application.port.output.OperationStore
import xyz.robinjoon.agentdocs.workspace.application.port.output.PersonalDocumentStore
import xyz.robinjoon.agentdocs.workspace.application.port.output.RevisionCatalog
import xyz.robinjoon.agentdocs.workspace.application.port.output.SearchCatalog
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceGuideCatalog
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceStatisticsCatalog
import xyz.robinjoon.agentdocs.workspace.application.port.output.ThreadRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.UploadGrantRepository

// The workspace's database ports, built once and shared by its use cases. The Exposed adapters keep no state; they
// join the transaction of the use case that calls them.
class WorkspacePersistence {
    val documentAssets: DocumentAssetStore = ExposedDocumentAssetStore()
    val spaceGuides: SpaceGuideCatalog = ExposedSpaceGuideCatalog()
    val personalDocuments: PersonalDocumentStore = ExposedPersonalDocumentStore()
    val navigation: NavigationCatalog = ExposedNavigationCatalog()
    val editSessions: EditSessionRepository = ExposedEditSessionRepository()
    val documentEvents: DocumentEventCatalog = ExposedDocumentEventCatalog()
    val exports: ExportCatalog = ExposedExportCatalog()

    val spaces: SpaceRepository = ExposedSpaceRepository()
    val statistics: SpaceStatisticsCatalog = ExposedSpaceStatisticsCatalog()
    val documents: DocumentRepository = ExposedDocumentRepository()
    val revisions: RevisionCatalog = ExposedRevisionCatalog()
    val threads: ThreadRepository = ExposedThreadRepository()
    val inbox: InboxRepository = ExposedInboxRepository()
    val assets: AssetRepository = ExposedAssetRepository()
    val uploadGrants: UploadGrantRepository = ExposedUploadGrantRepository()
    val search: SearchCatalog = ExposedSearchCatalog()
    val operations: OperationStore = ExposedOperationStore()
}
