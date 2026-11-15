package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentId
import xyz.robinjoon.agentdocs.workspace.domain.thread.Suggestion
import xyz.robinjoon.agentdocs.workspace.domain.thread.SuggestionBasis
import xyz.robinjoon.agentdocs.workspace.domain.thread.SuggestionDecision
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadClaim
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadCollaboration
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadReaction
import java.time.Instant

internal object ThreadCollaborationCodec {
    private val json = JsonMapper.builder().build()

    fun serialize(value: ThreadCollaboration): String = json.writeValueAsString(encode(value))

    fun deserialize(value: String?): ThreadCollaboration = value?.let { decode(json.readTree(it)) } ?: ThreadCollaboration.empty()

    fun encode(value: ThreadCollaboration): Map<String, Any?> =
        mapOf(
            "claim" to
                value.claim?.let {
                    mapOf(
                        "actor" to OperationActorSnapshotCodec.encode(it.actor),
                        "acquiredAt" to it.acquiredAt.toString(),
                        "expiresAt" to it.expiresAt.toString(),
                    )
                },
            "reactions" to value.reactions.map { mapOf("actor" to OperationActorSnapshotCodec.encode(it.actor), "emoji" to it.emoji) },
            "suggestions" to value.suggestions.map(::suggestion),
        )

    private fun suggestion(value: Suggestion): Map<String, Any?> =
        mapOf(
            "comment" to value.comment.value.toString(),
            "anchor" to ThreadSnapshotCodec.anchor(value.basis.anchor),
            "hash" to value.basis.hash,
            "before" to value.basis.before,
            "after" to value.basis.after,
            "decision" to
                value.decision?.let {
                    mapOf(
                        "actor" to OperationActorSnapshotCodec.encode(it.actor),
                        "at" to it.at.toString(),
                        "accepted" to it.accepted,
                        "revision" to it.revision,
                    )
                },
        )

    fun decode(node: JsonNode): ThreadCollaboration =
        ThreadCollaboration.restore(
            node.fieldNode("claim")?.let {
                ThreadClaim(
                    OperationActorSnapshotCodec.decode(it.path("actor")),
                    it.instant("acquiredAt"),
                    it.instant("expiresAt"),
                )
            },
            node.path("reactions").values().map { ThreadReaction(OperationActorSnapshotCodec.decode(it.path("actor")), it.text("emoji")) },
            node.path("suggestions").values().map(::readSuggestion),
        )

    private fun readSuggestion(node: JsonNode): Suggestion =
        Suggestion(
            CommentId(node.uuid("comment")),
            SuggestionBasis(
                ThreadSnapshotCodec.readAnchor(node.path("anchor")),
                node.text("hash"),
                node.text("before"),
                node.text("after"),
            ),
            node.fieldNode("decision")?.let {
                SuggestionDecision(
                    OperationActorSnapshotCodec.decode(it.path("actor")),
                    Instant.parse(it.text("at")),
                    it.path("accepted").booleanValue(),
                    it.fieldNode("revision")?.intValue(),
                )
            },
        )
}
