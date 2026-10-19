package xyz.robinjoon.agentdocs.workspace.domain.asset

import xyz.robinjoon.agentdocs.workspace.domain.Actor
import java.time.Instant

// Where the file came from: its original name, who uploaded it, and, for an image pasted by URL, the address it was
// downloaded from.
data class AssetOrigin(
    val fileName: String,
    val sourceUrl: String?,
    val uploadedBy: Actor,
    val uploadedAt: Instant,
) {
    init {
        if (fileName.isBlank() || fileName.length > MAX_FILE_NAME_LENGTH || fileName.any { it == '/' || it == '\\' || it.isISOControl() }) {
            throw InvalidAssetException("a file name is 1 to $MAX_FILE_NAME_LENGTH characters without slashes or control characters")
        }
    }

    companion object {
        const val MAX_FILE_NAME_LENGTH = 200
    }
}
