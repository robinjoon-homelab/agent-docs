package xyz.robinjoon.agentdocs.config

import xyz.robinjoon.agentdocs.workspace.application.port.output.AssetStore
import xyz.robinjoon.agentdocs.workspace.application.port.output.ImageVerifier
import xyz.robinjoon.agentdocs.workspace.application.port.output.RemoteImageSource
import xyz.robinjoon.agentdocs.workspace.application.port.output.SceneVerifier

// The ports the asset use case needs outside the database.
class AssetPorts(
    val store: AssetStore,
    val verifier: ImageVerifier,
    val remote: RemoteImageSource,
    val scenes: SceneVerifier,
)
