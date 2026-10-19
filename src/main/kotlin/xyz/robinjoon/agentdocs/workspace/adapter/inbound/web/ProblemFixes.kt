package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

// The next step an error tells the caller, the same over REST and MCP so an agent reads one wording.
internal object ProblemFixes {
    const val NOT_FOUND = "check the id; this connection only sees the spaces it was granted"
    const val UNAUTHENTICATED = "sign in, or send a bearer token from MCP OAuth or a script token"
    const val INVALID = "correct the value the message names and send the request again"
    const val TAKEN = "choose another path or slug"
}
