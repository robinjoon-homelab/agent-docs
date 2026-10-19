package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp

import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.McpToolResult
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.ThreadJson
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.Wire
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WorkspaceJson
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.ThreadChange
import xyz.robinjoon.agentdocs.workspace.application.model.ThreadQuery
import xyz.robinjoon.agentdocs.workspace.application.model.ThreadView
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.application.port.input.ActivityUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.SpaceUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.ThreadUseCase
import xyz.robinjoon.agentdocs.workspace.domain.ActorKind
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentId
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadStatus
import java.time.Instant

// How agents find work: threads people left, each with the anchored block and its neighbors, so the document does
// not have to be read in full first.
class CommentsQueryTool(
    private val threads: ThreadUseCase,
    private val spaces: SpaceUseCase,
) : McpTool {
    override val definition =
        ToolDefinition(
            name = "comments_query",
            title = "Find comment threads",
            description =
                "Threads in the spaces this connection sees, most recently changed first, at most 100. Defaults to open threads. Each " +
                    "result has " +
                    "the quote, the anchored block's current markdown, and the blocks around it.",
            properties =
                mapOf(
                    "space" to Schemas.string("Space slug"),
                    "document" to Schemas.string("Document id"),
                    "status" to Schemas.strings("Statuses: open, answered, resolved. Default [open]"),
                    "authorKind" to Schemas.choice("Who opened the thread", listOf("human", "agent")),
                    "mention" to Schemas.string("Only threads mentioning this GitHub login"),
                    "since" to Schemas.string("Only threads changed after this ISO-8601 instant"),
                ),
            required = emptyList(),
            readOnly = true,
        )

    override fun call(
        arguments: Arguments,
        context: WriteContext,
    ): McpToolResult {
        val space = arguments.optionalString("space")?.let { spaces.get(context.access, SpaceSlug(it)).id }
        val statuses = (arguments.strings("status") ?: listOf("open")).map { value -> statusOf(value) }.toSet()
        val query =
            ThreadQuery(
                space?.let(::setOf),
                arguments.optionalUuid("document")?.let(::DocumentId),
                statuses,
                Wire.actorKind(arguments.optionalString("authorKind"), "authorKind"),
                arguments.optionalString("mention")?.removePrefix("@"),
                arguments.optionalInstant("since"),
            )
        return McpToolResult.data(mapOf("threads" to threads.query(context.access, query).map(ThreadJson::thread)))
    }
}

class CommentsWriteTool(
    private val threads: ThreadUseCase,
) : McpTool {
    override val definition =
        ToolDefinition(
            name = "comments_write",
            title = "Write a comment",
            description =
                "action open: new thread on document with anchor {kind: text|cell|block|document, blockId, quote, start?, rowId?, " +
                    "columnId?} and body. " +
                    "action reply: reply to thread with body; markAnswered true when you handled the request. action answered or " +
                    "reopen: change status. " +
                    "action link: link revision to thread. action move: put thread on a new anchor. action edit or delete: change " +
                    "or remove your own comment. You cannot resolve a thread a person opened; mark it answered instead.",
            properties =
                mapOf(
                    "action" to
                        Schemas.choice(
                            "What to do",
                            listOf("open", "reply", "answered", "reopen", "resolve", "link", "move", "edit", "delete"),
                        ),
                    "document" to Schemas.string("open: document id"),
                    "anchor" to Schemas.obj("open, move: where the thread goes"),
                    "thread" to Schemas.string("Thread id for open's follow-ups: reply, status, link, move"),
                    "comment" to Schemas.string("edit, delete: comment id"),
                    "body" to Schemas.string("Comment markdown; @login mentions a person"),
                    "markAnswered" to Schemas.boolean("reply: also mark the thread answered"),
                    "revision" to Schemas.integer("link: revision number"),
                    "opId" to Schemas.operationId,
                ),
            required = listOf("action"),
            readOnly = false,
        )

    override fun call(
        arguments: Arguments,
        context: WriteContext,
    ): McpToolResult {
        val action = arguments.string("action")
        val view = statusChange(action, arguments, context) ?: write(action, arguments, context)
        return McpToolResult.data(mapOf("thread" to ThreadJson.thread(view)))
    }

    private fun write(
        action: String,
        arguments: Arguments,
        context: WriteContext,
    ): ThreadView =
        when (action) {
            "open" -> {
                val anchor = ThreadJson.anchorRequest(arguments.node.get("anchor"))
                threads.open(context, DocumentId(arguments.uuid("document")), anchor, arguments.string("body"))
            }

            "reply" -> {
                threads.reply(context, thread(arguments), arguments.string("body"), arguments.boolean("markAnswered"))
            }

            "link" -> {
                val revision = arguments.optionalInt("revision") ?: throw InvalidRequestException("link needs revision")
                threads.change(context, thread(arguments), ThreadChange(linkRevision = revision))
            }

            "move" -> {
                val anchor = arguments.node.get("anchor") ?: throw InvalidRequestException("move needs anchor")
                threads.change(context, thread(arguments), ThreadChange(anchor = ThreadJson.anchorRequest(anchor)))
            }

            "edit" -> {
                threads.editComment(context, CommentId(arguments.uuid("comment")), arguments.string("body"))
            }

            "delete" -> {
                threads.deleteComment(context, CommentId(arguments.uuid("comment")))
            }

            else -> {
                throw InvalidRequestException(
                    "action must be open, reply, answered, reopen, resolve, link, move, edit, or delete, not $action",
                )
            }
        }

    private fun statusChange(
        action: String,
        arguments: Arguments,
        context: WriteContext,
    ): ThreadView? {
        val status =
            when (action) {
                "answered" -> ThreadStatus.ANSWERED
                "reopen" -> ThreadStatus.OPEN
                "resolve" -> ThreadStatus.RESOLVED
                else -> return null
            }
        return threads.change(context, thread(arguments), ThreadChange(status = status))
    }

    private fun thread(arguments: Arguments): ThreadId = ThreadId(arguments.uuid("thread"))

    private fun result(view: ThreadView): McpToolResult = McpToolResult.data(mapOf("thread" to ThreadJson.thread(view)))
}

class ChangesSinceTool(
    private val activity: ActivityUseCase,
) : McpTool {
    override val definition =
        ToolDefinition(
            name = "changes_since",
            title = "What changed",
            description =
                "Document and comment changes in the spaces this connection sees after an ISO-8601 instant, oldest first. Pass " +
                    "nextSince to continue.",
            properties =
                mapOf(
                    "since" to Schemas.string("ISO-8601 instant"),
                    "limit" to Schemas.integer("At most this many entries, default 100"),
                ),
            required = listOf("since"),
            readOnly = true,
        )

    override fun call(
        arguments: Arguments,
        context: WriteContext,
    ): McpToolResult {
        val since = arguments.optionalInstant("since") ?: Instant.EPOCH
        val feed = activity.changesSince(context.access, since, arguments.optionalInt("limit") ?: DEFAULT_LIMIT)
        return McpToolResult.data(mapOf("entries" to feed.entries.map(WorkspaceJson::activity), "nextSince" to feed.nextSince))
    }

    private companion object {
        const val DEFAULT_LIMIT = 100
    }
}

private fun statusOf(value: String): ThreadStatus =
    ThreadStatus.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
        ?: throw InvalidRequestException("status must be open, answered, or resolved")
