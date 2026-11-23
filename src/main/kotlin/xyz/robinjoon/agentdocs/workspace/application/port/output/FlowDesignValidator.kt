package xyz.robinjoon.agentdocs.workspace.application.port.output

import xyz.robinjoon.agentdocs.workspace.application.model.DesignValidationWarning

interface FlowDesignValidator {
    /** Rejects an invalid candidate before writing; the previous source identifies retained orphan references. */
    fun validate(
        source: String,
        previousSource: String? = null,
    ): List<DesignValidationWarning>
}
