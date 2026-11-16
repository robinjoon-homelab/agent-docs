package xyz.robinjoon.agentdocs.workspace.application.service

import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.workspace.application.model.AssetFile
import xyz.robinjoon.agentdocs.workspace.application.model.AssetSource
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.port.input.AssetNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentAssetUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.output.AssetRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.AssetStore
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentAssetStore
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.SceneVerifier
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetMediaType
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId

@Transactional(readOnly = true)
class DocumentAssetService(
    private val documents: DocumentRepository,
    private val references: DocumentAssetStore,
    private val assets: AssetRepository,
    private val bytes: AssetStore,
    private val scenes: SceneVerifier,
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

    // Only text originals have a source; the same document-scoped check as content() decides what is visible.
    override fun source(
        access: Access,
        document: DocumentId,
        reference: AssetId,
    ): AssetSource {
        val file = content(access, document, reference)
        val type = file.asset.content.mediaType
        if (type !in textual) throw InvalidRequestException("only HTML, SVG, and drawing scene assets have source")
        val scene = if (type == AssetMediaType.EXCALIDRAW) scenes.verify(file.bytes) else null
        return AssetSource(file.asset, file.bytes.toString(Charsets.UTF_8), scene)
    }

    private companion object {
        val textual = setOf(AssetMediaType.HTML, AssetMediaType.SVG, AssetMediaType.EXCALIDRAW)
    }
}
