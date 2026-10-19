package xyz.robinjoon.agentdocs.workspace.application.service

import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.application.model.ActivitySubject
import xyz.robinjoon.agentdocs.workspace.application.model.AssetFile
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.UploadedFile
import xyz.robinjoon.agentdocs.workspace.application.model.VerifiedImage
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.application.port.input.AssetNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.input.AssetUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.SpaceNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.output.AssetRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.AssetStore
import xyz.robinjoon.agentdocs.workspace.application.port.output.ImageVerifier
import xyz.robinjoon.agentdocs.workspace.application.port.output.RemoteImageSource
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceRepository
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.Permission
import xyz.robinjoon.agentdocs.workspace.domain.asset.Asset
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetContent
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetOrigin
import xyz.robinjoon.agentdocs.workspace.domain.asset.InvalidAssetException
import xyz.robinjoon.agentdocs.workspace.domain.space.Space
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import java.util.UUID

// Every upload or import is its own asset with its own name and source address; identical bytes are stored once under
// their hash, which every asset with that content shares. The bytes go to the store before the row is written, so if
// the row then fails they are an unreferenced copy a retry reuses.
@Transactional(readOnly = true)
class AssetService(
    private val spaces: SpaceRepository,
    private val assets: AssetRepository,
    private val store: AssetStore,
    private val verifier: ImageVerifier,
    private val remote: RemoteImageSource,
    private val journal: WriteJournal,
) : AssetUseCase {
    @Transactional
    override fun upload(
        context: WriteContext,
        space: SpaceSlug,
        file: UploadedFile,
    ): Asset {
        val target = writable(context.access, space)
        return keep(context, target, verified(file.bytes), Source(file.fileName, null))
    }

    @Transactional
    override fun import(
        context: WriteContext,
        space: SpaceSlug,
        url: String,
    ): Asset {
        val target = writable(context.access, space)
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            throw InvalidRequestException("an image address must start with http:// or https://")
        }
        val image =
            remote.fetch(url)
                ?: throw InvalidRequestException("could not download a public image from $url", "download it and upload the file")
        return keep(context, target, verified(image.bytes), Source(url.substringBefore('?').substringAfterLast('/'), url))
    }

    override fun content(
        access: Access,
        id: AssetId,
    ): AssetFile {
        val asset = assets.findById(id)?.takeIf { access.sees(it.spaceId) } ?: throw AssetNotFoundException(id)
        return AssetFile(asset, store.get(asset.content.sha256))
    }

    private fun keep(
        context: WriteContext,
        space: Space,
        image: VerifiedImage,
        source: Source,
    ): Asset {
        val content = AssetContent.of(image.mediaType, image.bytes)
        store.put(content.sha256, content.mediaType.mime, image.bytes)
        val name =
            source.fileName.substringAfterLast('\\').trim().take(AssetOrigin.MAX_FILE_NAME_LENGTH).ifBlank {
                "image.${image.mediaType.extension}"
            }
        val origin = AssetOrigin(name, source.url, context.access.actor, journal.now())
        val asset = assets.add(Asset.upload(AssetId(UUID.randomUUID()), space.id, content, origin))
        journal.record(context, "asset.uploaded", ActivitySubject(space.id.value, null, null, null), "첨부 업로드: $name")
        return asset
    }

    private fun verified(bytes: ByteArray): VerifiedImage =
        verifier.verify(bytes) ?: throw InvalidAssetException("only PNG, JPEG, GIF, WebP, and SVG images can be uploaded")

    private fun writable(
        access: Access,
        slug: SpaceSlug,
    ): Space {
        val space = spaces.findBySlug(slug)?.takeIf { access.sees(it.id) } ?: throw SpaceNotFoundException(slug)
        Permissions.require(access, space.id, Permission.EDIT, "uploading files")
        return space
    }

    private data class Source(
        val fileName: String,
        val url: String?,
    )
}
