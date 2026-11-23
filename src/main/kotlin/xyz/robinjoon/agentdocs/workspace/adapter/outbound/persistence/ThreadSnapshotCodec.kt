package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import tools.jackson.databind.JsonNode
import xyz.robinjoon.agentdocs.workspace.application.model.ThreadContext
import xyz.robinjoon.agentdocs.workspace.application.model.ThreadView
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import xyz.robinjoon.agentdocs.workspace.domain.thread.Anchor
import xyz.robinjoon.agentdocs.workspace.domain.thread.AnchorResolution
import xyz.robinjoon.agentdocs.workspace.domain.thread.Comment
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentEdit
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentHistory
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentId
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentThread
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadLocation
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadProgress
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadStatus
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadTimeline
import java.time.Instant
import java.util.UUID

internal object ThreadSnapshotCodec {
    fun encode(view: ThreadView): Map<String, Any?> {
        val thread = view.thread
        val timeline = thread.progress.timeline
        val attached = view.resolution as? AnchorResolution.Attached
        return mapOf(
            "id" to thread.id.value.toString(),
            "space" to thread.location.spaceId.value,
            "document" to thread.location.documentId.value,
            "anchor" to anchor(thread.location.anchor),
            "revision" to thread.location.anchoredRevision,
            "status" to thread.status.name,
            "linked" to thread.progress.linkedRevisions,
            "createdAt" to timeline.createdAt.toString(),
            "updatedAt" to timeline.updatedAt.toString(),
            "resolvedAt" to timeline.resolvedAt?.toString(),
            "resolvedBy" to timeline.resolvedBy?.let(OperationActorSnapshotCodec::encode),
            "comments" to thread.comments.map(::comment),
            "collaboration" to ThreadCollaborationCodec.encode(thread.collaboration),
            "attached" to (attached != null),
            "start" to attached?.start,
            "end" to attached?.end,
            "context" to view.context?.let(::context),
            "written" to view.written?.value?.toString(),
        )
    }

    fun decode(node: JsonNode): ThreadView =
        ThreadView(
            readThread(node),
            readResolution(node),
            node.fieldNode("context")?.let(::readContext),
            node.optionalText("written")?.let { CommentId(UUID.fromString(it)) },
        )

    private fun readThread(node: JsonNode): CommentThread {
        val location =
            ThreadLocation(
                SpaceId(node.uuid("space")),
                DocumentId(node.uuid("document")),
                readAnchor(node.path("anchor")),
                node.path("revision").intValue(),
            )
        val timeline =
            ThreadTimeline(
                node.instant("createdAt"),
                node.instant("updatedAt"),
                node.optionalText("resolvedAt")?.let(Instant::parse),
                node.fieldNode("resolvedBy")?.let(OperationActorSnapshotCodec::decode),
            )
        val progress =
            ThreadProgress.restore(
                ThreadStatus.valueOf(node.text("status")),
                node.path("linked").values().map { it.intValue() },
                timeline,
            )
        return CommentThread
            .restore(
                ThreadId(node.uuid("id")),
                location,
                progress,
                node.path("comments").values().map(::readComment),
            ).restoreCollaboration(readCollaboration(node))
    }

    private fun readCollaboration(node: JsonNode) =
        node.fieldNode("collaboration")?.let(ThreadCollaborationCodec::decode)
            ?: xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadCollaboration
                .empty()

    private fun readResolution(node: JsonNode): AnchorResolution {
        if (!node.path("attached").booleanValue()) return AnchorResolution.Detached
        return AnchorResolution.Attached(node.fieldNode("start")?.intValue(), node.fieldNode("end")?.intValue())
    }

    private fun comment(comment: Comment): Map<String, Any?> =
        mapOf(
            "id" to comment.id.value.toString(),
            "author" to OperationActorSnapshotCodec.encode(comment.author),
            "body" to comment.body,
            "createdAt" to comment.history.createdAt.toString(),
            "deletedAt" to comment.history.deletedAt?.toString(),
            "edits" to comment.history.edits.map { mapOf("body" to it.previousBody, "at" to it.editedAt.toString()) },
        )

    private fun readComment(node: JsonNode): Comment {
        val edits = node.path("edits").values().map { CommentEdit(it.text("body"), it.instant("at")) }
        val history = CommentHistory.of(node.instant("createdAt"), edits, node.optionalText("deletedAt")?.let(Instant::parse))
        return Comment(CommentId(node.uuid("id")), OperationActorSnapshotCodec.decode(node.path("author")), node.text("body"), history)
    }

    fun anchor(anchor: Anchor): Map<String, Any?> =
        when (anchor) {
            Anchor.Document -> {
                mapOf("kind" to "document")
            }

            is Anchor.Text -> {
                mapOf(
                    "kind" to "text",
                    "block" to anchor.blockId.value,
                    "start" to anchor.start,
                    "end" to anchor.end,
                    "quote" to anchor.quote,
                )
            }

            is Anchor.Cell -> {
                mapOf(
                    "kind" to "cell",
                    "block" to anchor.blockId.value,
                    "row" to anchor.rowId.value,
                    "column" to anchor.columnId.value,
                    "quote" to anchor.quote,
                )
            }

            is Anchor.Whole -> {
                val state = anchor.viewedStateId?.let { mapOf("viewedStateId" to it.value) }.orEmpty()
                mapOf("kind" to "whole", "block" to anchor.blockId.value, "quote" to anchor.quote) + state
            }
        }

    fun readAnchor(node: JsonNode): Anchor =
        when (node.text("kind")) {
            "document" -> {
                Anchor.Document
            }

            "text" -> {
                Anchor.Text(
                    BlockId(node.text("block")),
                    node.path("start").intValue(),
                    node.path("end").intValue(),
                    node.text("quote"),
                )
            }

            "cell" -> {
                Anchor.Cell(BlockId(node.text("block")), BlockId(node.text("row")), BlockId(node.text("column")), node.text("quote"))
            }

            "whole" -> {
                Anchor.Whole(BlockId(node.text("block")), node.text("quote"), node.optionalText("viewedStateId")?.let(::BlockId))
            }

            else -> {
                error("unsupported snapshot anchor")
            }
        }

    private fun context(context: ThreadContext): Map<String, Any?> =
        mapOf(
            "space" to context.space.value,
            "path" to context.path.value,
            "title" to context.title,
            "anchor" to context.anchorBlock,
            "previous" to context.previousBlock,
            "next" to context.nextBlock,
        )

    private fun readContext(node: JsonNode): ThreadContext =
        ThreadContext(
            SpaceSlug(node.text("space")),
            DocumentPath(node.text("path")),
            node.text("title"),
            node.optionalText("anchor"),
            node.optionalText("previous"),
            node.optionalText("next"),
        )
}
