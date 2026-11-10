package xyz.robinjoon.agentdocs.workspace.application.port.output

interface UploadSecretSource {
    fun next(): String
}
