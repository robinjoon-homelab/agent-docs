package xyz.robinjoon.agentdocs.config

import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.ExposedAssetRepository
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.ExposedDocumentRepository
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.ExposedDocumentViewReporter
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.ExposedInboxRepository
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.ExposedOperationStore
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.ExposedRevisionCatalog
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.ExposedSearchCatalog
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.ExposedSpaceRepository
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.ExposedSpaceStatisticsCatalog
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.ExposedThreadRepository
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.ExposedUsageCatalog
import xyz.robinjoon.agentdocs.workspace.application.port.output.AssetRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentViewReporter
import xyz.robinjoon.agentdocs.workspace.application.port.output.InboxRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.OperationStore
import xyz.robinjoon.agentdocs.workspace.application.port.output.RevisionCatalog
import xyz.robinjoon.agentdocs.workspace.application.port.output.SearchCatalog
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceStatisticsCatalog
import xyz.robinjoon.agentdocs.workspace.application.port.output.ThreadRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.UsageCatalog

// The workspace's database ports, built once and shared by its use cases. The Exposed adapters keep no state; they
// join the transaction of the use case that calls them.
class WorkspacePersistence {
    val spaces: SpaceRepository = ExposedSpaceRepository()
    val statistics: SpaceStatisticsCatalog = ExposedSpaceStatisticsCatalog()
    val documents: DocumentRepository = ExposedDocumentRepository()
    val revisions: RevisionCatalog = ExposedRevisionCatalog()
    val threads: ThreadRepository = ExposedThreadRepository()
    val inbox: InboxRepository = ExposedInboxRepository()
    val assets: AssetRepository = ExposedAssetRepository()
    val search: SearchCatalog = ExposedSearchCatalog()
    val usage: UsageCatalog = ExposedUsageCatalog()
    val views: DocumentViewReporter = ExposedDocumentViewReporter()
    val operations: OperationStore = ExposedOperationStore()
}
