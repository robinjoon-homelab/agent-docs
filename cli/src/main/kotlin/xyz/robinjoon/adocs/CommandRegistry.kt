package xyz.robinjoon.adocs

internal data class Route(
    val method: String,
    val template: String,
)

// A new REST action needs a registry entry, without adding another copy of its business rules.
internal object CommandRegistry {
    val routes =
        mapOf(
            "spaces" to Route("GET", "/api/spaces"),
            "resolve" to Route("POST", "/api/spaces/resolve"),
            "list" to Route("GET", "/api/spaces/{space}/documents"),
            "search" to Route("GET", "/api/search"),
            "read" to Route("GET", "/api/documents/{id}/markdown"),
            "open" to Route("GET", "/api/documents/{id}"),
            "create" to Route("POST", "/api/spaces/{space}/documents"),
            "edit" to Route("POST", "/api/documents/{id}/edits"),
            "move" to Route("PATCH", "/api/documents/{id}"),
            "edit-comment" to Route("PATCH", "/api/comments/{id}"),
            "delete-comment" to Route("DELETE", "/api/comments/{id}"),
            "header" to Route("PATCH", "/api/documents/{id}"),
            "trash" to Route("POST", "/api/documents/{id}/trash"),
            "untrash" to Route("POST", "/api/documents/{id}/untrash"),
            "history" to Route("GET", "/api/documents/{id}/revisions"),
            "revision" to Route("GET", "/api/documents/{id}/revisions/{number}"),
            "diff" to Route("GET", "/api/documents/{id}/diff"),
            "restore" to Route("POST", "/api/documents/{id}/restore"),
            "name-revision" to Route("POST", "/api/documents/{id}/revisions/{number}/name"),
            "restore-block" to Route("POST", "/api/documents/{id}/restore-block"),
            "threads" to Route("GET", "/api/documents/{id}/threads"),
            "thread" to Route("POST", "/api/documents/{id}/threads"),
            "comment" to Route("POST", "/api/threads/{id}/comments"),
            "resolve-thread" to Route("PATCH", "/api/threads/{id}"),
            "reaction" to Route("POST", "/api/threads/{id}/reactions"),
            "claim" to Route("POST", "/api/threads/{id}/claim"),
            "changes" to Route("GET", "/api/changes"),
            "navigation" to Route("GET", "/api/navigation/recent"),
            "favorites" to Route("GET", "/api/navigation/favorites"),
            "favorite" to Route("PUT", "/api/navigation/documents/{id}/favorite"),
            "backlinks" to Route("GET", "/api/navigation/documents/{id}/backlinks"),
            "statistics" to Route("GET", "/api/documents/{id}/edit-statistics"),
        )

    fun uploadPath(
        space: String,
        opId: String?,
    ): String {
        val path = "/api/spaces/${encoded(space)}/assets"
        return if (opId == null) path else "$path?opId=${encoded(opId)}"
    }

    fun path(
        route: Route,
        args: Arguments,
        space: () -> String,
    ): String {
        var path = route.template
        if ("{space}" in path) path = path.replace("{space}", encoded(space()))
        if ("{id}" in path) path = path.replace("{id}", encoded(args.argument(1)))
        if ("{number}" in path) path = path.replace("{number}", encoded(args.argument(2)))
        val query = args.option("query")
        if (query != null) {
            require(!query.contains('#') && !query.contains('\r') && !query.contains('\n')) { "invalid query" }
            path += "?$query"
        }
        return path
    }
}
