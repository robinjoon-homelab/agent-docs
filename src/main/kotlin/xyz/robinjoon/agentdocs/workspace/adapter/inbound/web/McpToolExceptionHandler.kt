package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.McpToolResult
import xyz.robinjoon.agentdocs.workspace.application.model.AssetContentMissingException
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.port.input.AssetNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.input.CommentNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.input.InboxItemNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.input.PermissionDeniedException
import xyz.robinjoon.agentdocs.workspace.application.port.input.RevisionNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.input.SpaceNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.input.SpaceNotResolvedException
import xyz.robinjoon.agentdocs.workspace.application.port.input.StaleRevisionException
import xyz.robinjoon.agentdocs.workspace.application.port.input.ThreadNotFoundException
import xyz.robinjoon.agentdocs.workspace.domain.document.PathTakenException
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.EditConflictException
import xyz.robinjoon.agentdocs.workspace.domain.space.SlugTakenException
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadActionException

// The final error boundary of an MCP tool call. A business failure becomes a tool result with isError, the code the
// REST API uses, and a fix, so the agent can correct itself; anything else is rethrown unchanged.
object McpToolExceptionHandler {
    // quality-exception: MCP 도구 호출의 최종 오류 경계. 아는 업무 오류만 도구 오류로 바꾸고 나머지는 원인 그대로 다시 던진다.
    @Suppress("TooGenericExceptionCaught")
    fun handle(call: () -> McpToolResult): McpToolResult =
        try {
            call()
        } catch (exception: RuntimeException) {
            resultOf(exception) ?: throw exception
        }

    private fun resultOf(exception: RuntimeException): McpToolResult? {
        val message = exception.message.orEmpty()
        if (exception::class in notFound) return McpToolResult.error("not_found", message, ProblemFixes.NOT_FOUND)
        return when (exception) {
            is PermissionDeniedException -> denied(exception)
            is ThreadActionException -> McpToolResult.error("forbidden", message, exception.fix)
            is EditConflictException -> conflict(exception)
            is SpaceNotResolvedException -> unresolved(exception)
            is StaleRevisionException -> McpToolResult.error("conflict", message, "read the document again with docs_read and retry")
            is SlugTakenException, is PathTakenException -> McpToolResult.error("taken", message, ProblemFixes.TAKEN)
            is InvalidRequestException -> McpToolResult.error("invalid", message, exception.fix ?: ProblemFixes.INVALID)
            is IllegalArgumentException -> McpToolResult.error("invalid", message, ProblemFixes.INVALID)
            else -> null
        }
    }

    private fun denied(exception: PermissionDeniedException): McpToolResult =
        McpToolResult.error(
            "forbidden",
            exception.message.orEmpty(),
            "ask the owner to allow ${exception.needed.name.lowercase()} for this connection",
        )

    private fun conflict(exception: EditConflictException): McpToolResult {
        val conflicts =
            exception.conflicts.map {
                mapOf(
                    "blockId" to it.blockId.value,
                    "currentHash" to it.currentHash,
                    "current" to it.current,
                )
            }
        return McpToolResult.error("conflict", exception.message.orEmpty(), CONFLICT_FIX, mapOf("conflicts" to conflicts))
    }

    private fun unresolved(exception: SpaceNotResolvedException): McpToolResult =
        McpToolResult.error(
            "not_resolved",
            exception.message.orEmpty(),
            RESOLVE_FIX,
            mapOf("candidates" to exception.candidates.map(WorkspaceJson::space)),
        )

    private val notFound =
        setOf(
            SpaceNotFoundException::class,
            DocumentNotFoundException::class,
            ThreadNotFoundException::class,
            CommentNotFoundException::class,
            AssetNotFoundException::class,
            RevisionNotFoundException::class,
            InboxItemNotFoundException::class,
            AssetContentMissingException::class,
        )

    private const val CONFLICT_FIX =
        "someone changed these blocks after you read them; read them again with docs_read and resend against the current hash"
    private const val RESOLVE_FIX = "pick a candidate slug, or ask the owner to link this repository or directory to a space"
}
