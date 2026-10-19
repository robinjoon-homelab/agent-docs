package xyz.robinjoon.agentdocs.workspace.application.port.output

import xyz.robinjoon.agentdocs.workspace.application.model.VerifiedImage

interface ImageVerifier {
    // Detects the type from the bytes and returns null when they are not a supported image. SVG comes back with
    // scripts, event handlers, and external references removed.
    fun verify(bytes: ByteArray): VerifiedImage?
}
