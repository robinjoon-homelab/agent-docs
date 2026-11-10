package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.asset.Asset

// The asset a granted upload made, with the alt text the agent gave when it asked for the grant.
data class GrantedUpload(
    val asset: Asset,
    val alt: String,
)
