package xyz.robinjoon.agentdocs.access.adapter.inbound.web

// The scopes a connection can hold, from least to most. Clients request them all and the person picks one on the
// approval screen, so the metadata and the 401 challenge name the same set.
internal object OAuthScopes {
    val all = listOf("read", "comment", "edit")
}
