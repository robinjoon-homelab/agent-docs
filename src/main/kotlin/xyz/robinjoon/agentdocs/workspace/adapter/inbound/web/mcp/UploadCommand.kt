package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp

// The shell command an agent runs to send one local file. The agent fills in the path by plain substitution and a file's
// name can come from a repository or a download, so the path must not reach the shell whatever it holds. It sits alone
// in a quoted here-document, whose end marker comes from the token so that no name chosen earlier can close it. curl
// splits -F values at ; and , and reads " and \ specially, so the path is quoted for curl as well.
internal object UploadCommand {
    const val PATH_SLOT = "<FILE_PATH>"

    private const val MARKER_LENGTH = 16

    fun of(
        token: String,
        endpoint: String,
    ): String {
        val end = "ADT_PATH_" + token.filter(Char::isLetterOrDigit).takeLast(MARKER_LENGTH)
        val pathForCurl = """$(printf '%s' "${'$'}UPLOAD_FILE" | LC_ALL=C sed 's/[\\"]/\\&/g')"""
        return listOf(
            "IFS= read -r UPLOAD_FILE <<'$end'",
            PATH_SLOT,
            end,
            """curl -sS -H 'Authorization: Bearer $token' -F "file=@\"$pathForCurl\"" '$endpoint'""",
        ).joinToString("\n")
    }
}
