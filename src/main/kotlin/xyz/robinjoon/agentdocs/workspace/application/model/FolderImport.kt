package xyz.robinjoon.agentdocs.workspace.application.model

class FolderFile(
    val path: String,
    val mediaType: String?,
    val bytes: ByteArray,
)

data class FolderImportResult(
    val files: List<ImportedFile>,
)

data class ImportedFile(
    val path: String,
    val status: String,
    val documentId: String? = null,
    val code: String? = null,
    val relatedPath: String? = null,
)
