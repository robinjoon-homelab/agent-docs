package xyz.robinjoon.agentdocs.workspace.application.model

// One optional setting in a change: set it to a value or clear it. A setting the change leaves out is null and keeps
// its current value, so leaving a field out and clearing it never mean the same thing.
sealed interface SettingUpdate<out T> {
    data class To<T>(
        val value: T,
    ) : SettingUpdate<T>

    data object Clear : SettingUpdate<Nothing>
}
