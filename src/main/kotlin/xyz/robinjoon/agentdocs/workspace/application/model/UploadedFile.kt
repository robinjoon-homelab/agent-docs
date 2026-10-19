package xyz.robinjoon.agentdocs.workspace.application.model

// A file as it arrived: the name and type the client claimed, and the bytes. The type is checked against the bytes.
class UploadedFile(
    val fileName: String,
    val declaredType: String?,
    val bytes: ByteArray,
)
