package xyz.robinjoon.agentdocs.workspace.application.service

import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.workspace.application.model.AssetFile
import xyz.robinjoon.agentdocs.workspace.application.port.input.AssetNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentAssetUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.output.AssetRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.AssetStore
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentAssetStore
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentRepository
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId

@Transactional(readOnly = true)
class DocumentAssetService(
    private val documents: DocumentRepository,
    private val references: DocumentAssetStore,
    private val assets: AssetRepository,
    private val bytes: AssetStore,
) : DocumentAssetUseCase {
    override fun content(
        access: Access,
        document: DocumentId,
        reference: AssetId,
    ): AssetFile {
        val current = documents.findById(document)?.takeIf { access.sees(it.spaceId) } ?: throw DocumentNotFoundException(document)
        if (reference !in references.references(document)) throw AssetNotFoundException(reference)
        val actual = references.bindings(setOf(document)).firstOrNull { it.referencedAssetId == reference }?.storedAssetId ?: reference
        val asset = assets.findById(actual)?.takeIf { it.spaceId == current.spaceId } ?: throw AssetNotFoundException(reference)
        return AssetFile(asset, bytes.get(asset.content.sha256))
    }
}
