package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures
import xyz.robinjoon.agentdocs.workspace.application.model.ThreadView
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.thread.Anchor
import xyz.robinjoon.agentdocs.workspace.domain.thread.AnchorResolution
import xyz.robinjoon.agentdocs.workspace.domain.thread.Comment
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentId
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentThread
import xyz.robinjoon.agentdocs.workspace.domain.thread.Suggestion
import xyz.robinjoon.agentdocs.workspace.domain.thread.SuggestionBasis
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadCollaboration
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadLocation
import java.util.UUID

class ThreadCollaborationCodecTest {
    private val now = WorkspaceFixtures.now
    private val actor = WorkspaceFixtures.agent
    private val comment = Comment.write(CommentId(UUID.randomUUID()), actor, "수정 제안", now)
    private val basis = SuggestionBasis(Anchor.Whole(BlockId("intro1"), "old"), "abcdef01", "old", "new")

    @Test
    fun `claim times and suggestion content survive the exact result codec`() {
        val collaboration =
            ThreadCollaboration
                .empty()
                .claim(actor, now, "acquire")
                .react(actor, "👍", true)
                .suggested(Suggestion(comment.id, basis))
        assertThat(ThreadCollaborationCodec.deserialize(ThreadCollaborationCodec.serialize(collaboration))).isEqualTo(collaboration)
        assertThat(ThreadCollaborationCodec.deserialize(null)).isEqualTo(ThreadCollaboration.empty())
    }

    @Test
    fun `deleted suggestion original content is absent from the thread snapshot`() {
        val location = ThreadLocation(WorkspaceFixtures.spaceId, WorkspaceFixtures.documentId, basis.anchor, 1)
        val opened = CommentThread.open(ThreadId(UUID.randomUUID()), location, comment, now)
        val proposed = opened.collaborate(opened.collaboration.suggested(Suggestion(comment.id, basis)), now)
        val deleted = proposed.deleteComment(comment.id, actor, now)
        val encoded = ThreadSnapshotCodec.encode(ThreadView(deleted, AnchorResolution.Attached(null, null), null))
        assertThat(JsonMapper.builder().build().writeValueAsString(encoded)).doesNotContain("abcdef01")
        assertThat(deleted.collaboration.suggestions).isEmpty()
        assertThat(deleted.comments.single().body).isEmpty()
    }
}
