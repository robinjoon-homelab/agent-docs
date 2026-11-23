package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import tools.jackson.databind.JsonNode
import xyz.robinjoon.agentdocs.workspace.application.model.AnchorRequest
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.ThreadView
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.thread.Anchor
import xyz.robinjoon.agentdocs.workspace.domain.thread.AnchorResolution
import xyz.robinjoon.agentdocs.workspace.domain.thread.Comment
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadReaction

object ThreadJson {
    fun thread(
        view: ThreadView,
        access: Access? = null,
    ): Map<String, Any?> {
        val thread = view.thread
        val timeline = thread.progress.timeline
        val base =
            mapOf(
                "id" to thread.id.value,
                "documentId" to thread.location.documentId.value,
                "anchor" to anchor(thread.location.anchor),
                "anchoredRevision" to thread.location.anchoredRevision,
                "resolution" to resolution(view.resolution),
                "status" to thread.status.name.lowercase(),
                "author" to WorkspaceJson.actor(thread.author),
                "comments" to thread.comments.map { comment(it, access) },
                "claim" to thread.collaboration.claim?.let(::claim),
                "reactions" to thread.collaboration.reactions.map { reaction(it, access) },
                "suggestions" to thread.collaboration.suggestions.map(::suggestion),
                "linkedRevisions" to thread.progress.linkedRevisions,
                "createdAt" to timeline.createdAt,
                "updatedAt" to timeline.updatedAt,
                "resolvedAt" to timeline.resolvedAt,
            ) + contextOf(view)
        return access?.let { base + ("own" to thread.author.isSameAuthorAs(it.actor)) } ?: base
    }

    private fun claim(claim: xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadClaim): Map<String, Any?> =
        mapOf("actor" to WorkspaceJson.actor(claim.actor), "acquiredAt" to claim.acquiredAt, "expiresAt" to claim.expiresAt)

    private fun reaction(
        reaction: ThreadReaction,
        access: Access?,
    ): Map<String, Any?> {
        val base = mapOf("actor" to WorkspaceJson.actor(reaction.actor), "emoji" to reaction.emoji)
        return access?.let { base + ("own" to reaction.actor.isSameAuthorAs(it.actor)) } ?: base
    }

    private fun suggestion(value: xyz.robinjoon.agentdocs.workspace.domain.thread.Suggestion): Map<String, Any?> =
        mapOf(
            "commentId" to value.comment.value,
            "anchor" to anchor(value.basis.anchor),
            "hash" to value.basis.hash,
            "before" to value.basis.before,
            "after" to value.basis.after,
            "decision" to
                value.decision?.let {
                    mapOf(
                        "actor" to WorkspaceJson.actor(it.actor),
                        "at" to it.at,
                        "accepted" to it.accepted,
                        "revision" to it.revision,
                    )
                },
        )

    fun comment(
        comment: Comment,
        access: Access? = null,
    ): Map<String, Any?> {
        val base =
            mapOf(
                "id" to comment.id.value,
                "author" to WorkspaceJson.actor(comment.author),
                "body" to comment.body,
                "createdAt" to comment.history.createdAt,
                "editedAt" to comment.editedAt,
                "deleted" to comment.isDeleted,
            )
        return access?.let { base + ("own" to (!comment.isDeleted && comment.author.isSameAuthorAs(it.actor))) } ?: base
    }

    fun anchor(anchor: Anchor): Map<String, Any?> =
        when (anchor) {
            is Anchor.Text -> {
                mapOf(
                    "kind" to "text",
                    "blockId" to anchor.blockId.value,
                    "start" to anchor.start,
                    "end" to anchor.end,
                    "quote" to anchor.quote,
                )
            }

            is Anchor.Cell -> {
                mapOf(
                    "kind" to "cell",
                    "blockId" to anchor.blockId.value,
                    "rowId" to anchor.rowId.value,
                    "columnId" to anchor.columnId.value,
                    "quote" to anchor.quote,
                )
            }

            is Anchor.Whole -> {
                val state = anchor.viewedStateId?.let { mapOf("viewedStateId" to it.value) }.orEmpty()
                mapOf("kind" to "block", "blockId" to anchor.blockId.value, "quote" to anchor.quote) + state
            }

            Anchor.Document -> {
                mapOf("kind" to "document")
            }
        }

    fun anchorRequest(node: JsonNode?): AnchorRequest {
        if (node == null || node.isNull) throw InvalidRequestException("anchor is required")
        val block = { BlockId(node.path("blockId").asString("")) }
        val kind = node.path("kind").asString("")
        val state = node.get("viewedStateId")?.takeUnless(JsonNode::isNull)
        if (state != null &&
            (kind != "block" || !state.isString)
        ) {
            throw InvalidRequestException("viewedStateId needs a block anchor and string ID")
        }
        return when (kind) {
            "text" -> {
                AnchorRequest.Text(block(), node.path("quote").asString(""), node.get("start")?.takeIf(JsonNode::isInt)?.intValue())
            }

            "cell" -> {
                AnchorRequest.Cell(block(), BlockId(node.path("rowId").asString("")), BlockId(node.path("columnId").asString("")))
            }

            "block" -> {
                AnchorRequest.Whole(block(), state?.stringValue()?.let(::BlockId))
            }

            "document" -> {
                AnchorRequest.Document
            }

            else -> {
                throw InvalidRequestException("anchor kind must be text, cell, block, or document, not '$kind'")
            }
        }
    }

    private fun resolution(resolution: AnchorResolution): Map<String, Any?> =
        when (resolution) {
            is AnchorResolution.Attached -> mapOf("state" to "attached", "start" to resolution.start, "end" to resolution.end)
            AnchorResolution.Detached -> mapOf("state" to "detached")
        }

    private fun contextOf(view: ThreadView): Map<String, Any?> {
        val context = view.context ?: return emptyMap()
        return mapOf(
            "context" to
                mapOf(
                    "space" to context.space.value,
                    "path" to context.path.value,
                    "title" to context.title,
                    "anchorBlock" to context.anchorBlock,
                    "previousBlock" to context.previousBlock,
                    "nextBlock" to context.nextBlock,
                ),
        )
    }
}
