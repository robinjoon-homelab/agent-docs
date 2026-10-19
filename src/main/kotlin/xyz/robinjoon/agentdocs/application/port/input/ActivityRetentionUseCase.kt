package xyz.robinjoon.agentdocs.application.port.input

interface ActivityRetentionUseCase {
    // Drops log entries older than one year. Returns how many were dropped.
    fun prune(): Int
}
