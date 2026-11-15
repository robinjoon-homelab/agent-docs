package xyz.robinjoon.agentdocs.workspace.application.model

object ImportPaths {
    fun safe(path: String): Boolean =
        path.isNotBlank() && !path.startsWith('/') && !path.contains('\\') &&
            !path.contains(':') && !path.contains('%') && path.none { it.code < 32 } &&
            path.split('/').none { it.isEmpty() || it == "." || it == ".." }

    private fun removeParent(parts: MutableList<String>) {
        if (parts.isEmpty()) throw InvalidRequestException("unsafe_image_path")
        parts.removeLast()
    }

    fun resolve(
        document: String,
        image: String,
    ): String {
        if (image.startsWith('/') || image.contains(':') || image.contains('\\') || image.contains('%')) {
            throw InvalidRequestException("unsafe_image_path")
        }
        val parts = document.split('/').dropLast(1).toMutableList()
        image.split('/').forEach {
            when (it) {
                ".." -> removeParent(parts)
                "." -> Unit
                "" -> throw InvalidRequestException("unsafe_image_path")
                else -> parts.add(it)
            }
        }
        return parts.joinToString("/").also { if (!safe(it)) throw InvalidRequestException("unsafe_image_path") }
    }
}
