package xyz.robinjoon.agentdocs.workspace.application.port.output

import xyz.robinjoon.agentdocs.workspace.application.model.VerifiedScene

interface SceneVerifier {
    // Checks an Excalidraw scene and reports its texts without touching the bytes, which are stored as given. Anything
    // that is not a safe scene fails with InvalidAssetException.
    fun verify(bytes: ByteArray): VerifiedScene
}
