package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import xyz.robinjoon.agentdocs.workspace.application.model.BlockMark
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentWrite
import xyz.robinjoon.agentdocs.workspace.application.model.OperationRecord
import xyz.robinjoon.agentdocs.workspace.application.model.RelatedChange
import xyz.robinjoon.agentdocs.workspace.application.model.RelatedWrite
import xyz.robinjoon.agentdocs.workspace.application.model.RevisionLabel
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceReference
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentHeader
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentLifecycle
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentProperties
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentStatus
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentId
import java.time.Instant
import java.util.UUID

// Versioned, self-contained results. No part of a decoded snapshot is read from current rows.
internal object OperationSnapshotCodec {
    private val json = JsonMapper.builder().build()

    fun encode(record: OperationRecord): String? {
        if (record.documentWrite == null && record.threadView == null && record.revisionLabel == null &&
            record.assetAlt == null
        ) {
            return null
        }
        return json.writeValueAsString(
            mapOf(
                "version" to 1,
                "assetAlt" to record.assetAlt,
                "revisionLabel" to
                    record.revisionLabel?.let {
                        mapOf("document" to it.documentId.value.toString(), "revision" to it.revision, "name" to it.name)
                    },
                "documentWrite" to record.documentWrite?.let(::write),
                "threadView" to record.threadView?.let(ThreadSnapshotCodec::encode),
            ),
        )
    }

    fun decode(
        record: OperationRecord,
        payload: String,
    ): OperationRecord {
        val node = json.readTree(payload)
        require(node.path("version").intValue() == 1) { "unsupported operation snapshot version" }
        return record.copy(
            documentWrite = node.fieldNode("documentWrite")?.let(::readWrite),
            threadView = node.fieldNode("threadView")?.let(ThreadSnapshotCodec::decode),
            assetAlt = node.optionalText("assetAlt"),
            revisionLabel =
                node.fieldNode("revisionLabel")?.let {
                    RevisionLabel(
                        DocumentId(it.uuid("document")),
                        it.path("revision").intValue(),
                        it.optionalText("name"),
                    )
                },
        )
    }

    fun containsComment(
        payload: String,
        comment: CommentId,
    ): Boolean {
        val comments = json.readTree(payload).path("threadView").path("comments")
        return comments.values().any { containsCommentContent(it, comment) }
    }

    private fun containsCommentContent(
        node: JsonNode,
        comment: CommentId,
    ): Boolean {
        if (node.uuid("id") != comment.value) return false
        return node.text("body").isNotEmpty() || node.path("edits").values().any { it.text("body").isNotEmpty() }
    }

    fun containsDocument(
        payload: String,
        documents: Set<UUID>,
    ): Boolean {
        val node = json.readTree(payload)
        val write = node.path("documentWrite")
        val primary = write.path("document").optionalText("id")
        val threadDocument = node.path("threadView").optionalText("document")
        return node.path("revisionLabel").optionalText("document")?.let(UUID::fromString) in documents ||
            primary?.let(UUID::fromString) in documents ||
            threadDocument?.let(UUID::fromString) in documents ||
            write.path("related").values().any { it.path("document").uuid("id") in documents }
    }

    private fun write(write: DocumentWrite): Map<String, Any?> =
        mapOf(
            "document" to document(write.document),
            "revision" to write.revision,
            "changedBlocks" to write.changedBlocks.map { it.value },
            "amended" to write.amended,
            "unchanged" to write.unchanged,
            "merged" to write.merged,
            "marks" to write.marks.map(::mark),
            "space" to write.space?.let { mapOf("id" to it.id.value.toString(), "slug" to it.slug.value, "name" to it.name) },
            "related" to write.related.map { mapOf("document" to document(it.document), "change" to it.change.name) },
        )

    private fun readWrite(node: JsonNode): DocumentWrite =
        DocumentWrite(
            readDocument(node.path("document")),
            node.path("revision").intValue(),
            node.path("changedBlocks").values().map { BlockId(it.stringValue()) },
            node.path("amended").booleanValue(),
            node.path("unchanged").booleanValue(),
            node.path("merged").booleanValue(),
            node.path("related").values().map { RelatedWrite(readDocument(it.path("document")), RelatedChange.valueOf(it.text("change"))) },
            node.path("marks").values().map(::readMark),
            node
                .fieldNode(
                    "space",
                )?.let { SpaceReference(SpaceId(UUID.fromString(it.text("id"))), SpaceSlug(it.text("slug")), it.text("name")) },
        )

    private fun mark(mark: BlockMark): Map<String, Any?> =
        mapOf(
            "block" to mark.blockId.value,
            "actor" to OperationActorSnapshotCodec.encode(mark.actor),
            "revision" to mark.revision,
            "at" to mark.at.toString(),
        )

    private fun readMark(node: JsonNode): BlockMark =
        BlockMark(
            BlockId(node.text("block")),
            OperationActorSnapshotCodec.decode(node.path("actor")),
            node.path("revision").intValue(),
            node.instant("at"),
        )

    private fun document(document: Document): Map<String, Any?> {
        val header = document.header
        val life = document.lifecycle
        return mapOf(
            "id" to document.id.value.toString(),
            "space" to header.spaceId.value.toString(),
            "path" to header.path.value,
            "title" to header.title,
            "type" to header.type,
            "tags" to header.properties.tags,
            "status" to header.properties.status.name,
            "assignee" to header.properties.assignee,
            "body" to DocumentRows.bodyOf(document.blocks),
            "revision" to life.revision,
            "version" to life.version,
            "createdAt" to life.createdAt.toString(),
            "updatedAt" to life.updatedAt.toString(),
            "trashedAt" to life.trashedAt?.toString(),
        )
    }

    private fun readDocument(node: JsonNode): Document {
        val properties =
            DocumentProperties.of(
                node.path("tags").values().map {
                    it.stringValue()
                },
                DocumentStatus.valueOf(node.text("status")),
                node.optionalText("assignee"),
            )
        val header =
            DocumentHeader(
                SpaceId(node.uuid("space")),
                DocumentPath(node.text("path")),
                node.text("title"),
                properties,
                node.optionalText("type") ?: "doc",
            )
        val lifecycle =
            DocumentLifecycle(
                node.path("revision").intValue(),
                node.instant("createdAt"),
                node.instant("updatedAt"),
                node.optionalText("trashedAt")?.let(Instant::parse),
                node.path("version").intValue(),
            )
        return Document.restore(DocumentId(node.uuid("id")), header, DocumentRows.blocksOf(node.text("body"), header.type), lifecycle)
    }
}

internal fun JsonNode.fieldNode(field: String): JsonNode? = get(field)?.takeUnless { it.isNull }

internal fun JsonNode.text(field: String): String = path(field).stringValue()

internal fun JsonNode.optionalText(field: String): String? = fieldNode(field)?.stringValue()

internal fun JsonNode.uuid(field: String): UUID = UUID.fromString(text(field))

internal fun JsonNode.instant(field: String): Instant = Instant.parse(text(field))
