package xyz.robinjoon.agentdocs.workspace.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.agent
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.documentId
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.now
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.owner
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.spaceId
import xyz.robinjoon.agentdocs.workspace.application.service.InboxRecipients
import xyz.robinjoon.agentdocs.workspace.domain.Actor
import xyz.robinjoon.agentdocs.workspace.domain.inbox.InboxKind
import xyz.robinjoon.agentdocs.workspace.domain.thread.Anchor
import xyz.robinjoon.agentdocs.workspace.domain.thread.Comment
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentId
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentThread
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadLocation
import java.util.UUID

class InboxRecipientsTest {
    private val octocat = Actor.human(UUID.randomUUID(), "octocat")
    private val opened =
        CommentThread.open(
            ThreadId(UUID.randomUUID()),
            ThreadLocation(spaceId, documentId, Anchor.Document, 1),
            comment(owner, "Please check"),
            now,
        )

    @Test
    fun `a person hears once about a comment, mentions first, and never about their own words`() {
        val reply = comment(octocat, "Done, @Robin and @hubot. cc @octocat")
        val thread = opened.reply(reply, now)

        assertThat(InboxRecipients.of(thread, reply, "hubot")).isEqualTo(mapOf(InboxKind.MENTION to setOf("robin", "hubot")))
    }

    @Test
    fun `a person hears about their own agent's reply and the assignee hears about the rest`() {
        val reply = comment(agent, "Changed the delay to 30 seconds.")
        val thread = opened.reply(reply, now)

        assertThat(InboxRecipients.of(thread, reply, "octocat"))
            .isEqualTo(mapOf(InboxKind.REPLY to setOf("robin"), InboxKind.ASSIGNED_COMMENT to setOf("octocat")))
    }

    @Test
    fun `the first comment notifies only mentions and the assignee`() {
        assertThat(InboxRecipients.of(opened, opened.comments.single(), null)).isEmpty()
    }

    private fun comment(
        author: Actor,
        body: String,
    ): Comment = Comment.write(CommentId(UUID.randomUUID()), author, body, now)
}
