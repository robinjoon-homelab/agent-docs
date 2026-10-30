package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
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
import xyz.robinjoon.agentdocs.workspace.domain.InvalidActorException
import xyz.robinjoon.agentdocs.workspace.domain.asset.InvalidAssetException
import xyz.robinjoon.agentdocs.workspace.domain.document.InvalidDocumentException
import xyz.robinjoon.agentdocs.workspace.domain.document.PathTakenException
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.EditConflictException
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.InvalidEditException
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.MarkdownReader
import xyz.robinjoon.agentdocs.workspace.domain.space.InvalidSpaceException
import xyz.robinjoon.agentdocs.workspace.domain.space.SlugTakenException
import xyz.robinjoon.agentdocs.workspace.domain.thread.InvalidThreadException
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadActionException

// Applies only to the workspace controllers. Every problem carries a code to branch on and, where it helps, a fix.
// Something the caller may not see is not found, never forbidden, so its existence does not leak.
@RestControllerAdvice(
    assignableTypes = [
        SpaceController::class, DocumentController::class, HistoryController::class, ThreadController::class, AssetController::class,
        SearchController::class, InboxController::class, ActivityController::class,
    ],
)
@Order(Ordered.HIGHEST_PRECEDENCE)
class WorkspaceExceptionHandler : ResponseEntityExceptionHandler() {
    @ExceptionHandler(
        SpaceNotFoundException::class,
        DocumentNotFoundException::class,
        ThreadNotFoundException::class,
        CommentNotFoundException::class,
        AssetNotFoundException::class,
        RevisionNotFoundException::class,
        InboxItemNotFoundException::class,
        AssetContentMissingException::class,
    )
    fun handleNotFound(exception: RuntimeException): ProblemDetail =
        problem(HttpStatus.NOT_FOUND, "not_found", exception, ProblemFixes.NOT_FOUND)

    @ExceptionHandler(UnauthenticatedException::class)
    fun handleUnauthenticated(exception: UnauthenticatedException): ProblemDetail =
        problem(HttpStatus.UNAUTHORIZED, "unauthenticated", exception, ProblemFixes.UNAUTHENTICATED)

    @ExceptionHandler(PermissionDeniedException::class)
    fun handlePermission(exception: PermissionDeniedException): ProblemDetail =
        problem(
            HttpStatus.FORBIDDEN,
            "forbidden",
            exception,
            "ask the owner to allow ${exception.needed.name.lowercase()} for this connection",
        )

    @ExceptionHandler(ThreadActionException::class)
    fun handleThreadAction(exception: ThreadActionException): ProblemDetail =
        problem(HttpStatus.FORBIDDEN, "forbidden", exception, exception.fix)

    @ExceptionHandler(InvalidRequestException::class)
    fun handleInvalidRequest(exception: InvalidRequestException): ProblemDetail =
        problem(HttpStatus.BAD_REQUEST, "invalid", exception, exception.fix ?: ProblemFixes.INVALID)

    @ExceptionHandler(
        InvalidDocumentException::class,
        InvalidSpaceException::class,
        InvalidThreadException::class,
        InvalidAssetException::class,
        InvalidEditException::class,
        InvalidActorException::class,
    )
    fun handleInvalid(exception: IllegalArgumentException): ProblemDetail =
        problem(HttpStatus.BAD_REQUEST, "invalid", exception, ProblemFixes.INVALID)

    @ExceptionHandler(StaleRevisionException::class)
    fun handleStale(exception: StaleRevisionException): ProblemDetail =
        problem(HttpStatus.CONFLICT, "conflict", exception, "read the document again and resend the change against its current revision")

    @ExceptionHandler(SlugTakenException::class)
    fun handleTaken(exception: SlugTakenException): ProblemDetail = problem(HttpStatus.CONFLICT, "taken", exception, ProblemFixes.TAKEN)

    // The path lets the screen show which document is in the way, also when it is under the one being moved.
    @ExceptionHandler(PathTakenException::class)
    fun handlePathTaken(exception: PathTakenException): ProblemDetail {
        val problem = problem(HttpStatus.CONFLICT, "taken", exception, ProblemFixes.TAKEN)
        problem.setProperty("path", exception.path.value)
        return problem
    }

    // Each conflict carries the block as it is now, as markdown for agents and as JSON for the web editor's conflict card.
    @ExceptionHandler(EditConflictException::class)
    fun handleConflict(exception: EditConflictException): ProblemDetail {
        val problem =
            problem(HttpStatus.CONFLICT, "conflict", exception, "read the blocks again and resend the change against their current hash")
        val conflicts =
            exception.conflicts.map { conflict ->
                val current =
                    conflict.current?.let {
                        MarkdownReader
                            .readBlocks(it)
                            .firstOrNull()
                            ?.block
                            ?.withId(conflict.blockId)
                    }
                mapOf(
                    "blockId" to conflict.blockId.value,
                    "currentHash" to conflict.currentHash,
                    "currentMarkdown" to conflict.current,
                    "current" to current?.let(BlockJson::write),
                )
            }
        problem.setProperty("conflicts", conflicts)
        return problem
    }

    @ExceptionHandler(SpaceNotResolvedException::class)
    fun handleNotResolved(exception: SpaceNotResolvedException): ProblemDetail {
        val problem =
            problem(
                HttpStatus.NOT_FOUND,
                "not_resolved",
                exception,
                "link the repository or directory to a space in its settings, or pick a candidate",
            )
        problem.setProperty("candidates", exception.candidates.map(WorkspaceJson::space))
        return problem
    }

    private fun problem(
        status: HttpStatus,
        code: String,
        exception: RuntimeException,
        fix: String?,
    ): ProblemDetail {
        val problem = ProblemDetail.forStatusAndDetail(status, exception.message.orEmpty())
        problem.title = status.reasonPhrase
        problem.setProperty("code", code)
        if (fix != null) problem.setProperty("fix", fix)
        return problem
    }
}
