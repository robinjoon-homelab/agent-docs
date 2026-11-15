package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.jdbc.insert
import xyz.robinjoon.agentdocs.support.persistence.MigratedH2Database
import xyz.robinjoon.agentdocs.workspace.domain.asset.Asset
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetContent
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetMediaType
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetOrigin
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.MarkdownReader
import xyz.robinjoon.agentdocs.workspace.domain.space.Space
import xyz.robinjoon.agentdocs.workspace.domain.thread.Anchor
import xyz.robinjoon.agentdocs.workspace.domain.thread.Comment
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentId
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentThread
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadLocation
import java.util.UUID

internal class TransferPersistenceFixtures(
    private val database: MigratedH2Database,
) {
    val source = database.inTransaction { ExposedSpaceRepository().add(PersistenceFixtures.space("source")) }
    val target = database.inTransaction { ExposedSpaceRepository().add(PersistenceFixtures.space("target")) }

    fun asset(space: Space): Asset {
        val asset =
            Asset.upload(
                AssetId(UUID.randomUUID()),
                space.id,
                AssetContent.of(AssetMediaType.PNG, byteArrayOf(1)),
                AssetOrigin("image.png", null, PersistenceFixtures.person, PersistenceFixtures.at),
            )
        return database.inTransaction { ExposedAssetRepository().add(asset) }
    }

    fun document(
        space: Space,
        path: String,
        markdown: String,
        history: Boolean = true,
    ): Document {
        val doc = PersistenceFixtures.document(space.id, path, MarkdownReader.readBlocks(markdown).map { it.block })
        database.inTransaction {
            DocumentsTable.insert {
                it[id] = doc.id.value
                it[createdAt] = doc.lifecycle.createdAt.utc()
                DocumentRows.write(it, doc)
            }
            if (history) RevisionRows.insert(PersistenceFixtures.revisionOf(doc))
        }
        return doc
    }

    fun comment(
        document: Document,
        before: String,
        after: String?,
    ): CommentThread {
        val comment = Comment.write(CommentId(UUID.randomUUID()), PersistenceFixtures.person, before, PersistenceFixtures.at)
        val thread =
            CommentThread.open(
                ThreadId(UUID.randomUUID()),
                ThreadLocation(
                    document.spaceId,
                    document.id,
                    Anchor.Document,
                    1,
                ),
                comment,
                PersistenceFixtures.at,
            )
        val edited =
            if (after == null) {
                thread
            } else {
                thread.editComment(
                    comment.id,
                    PersistenceFixtures.person,
                    after,
                    PersistenceFixtures.at.plusSeconds(1),
                )
            }
        database.inTransaction {
            ThreadsTable.insert {
                it[id] = edited.id.value
                it[createdAt] = PersistenceFixtures.at.utc()
                ThreadRows.write(it, edited)
            }
            ThreadRows.writeChildren(edited)
        }
        return edited
    }
}
