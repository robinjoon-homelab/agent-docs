package xyz.robinjoon.agentdocs.workspace.domain.document

import java.util.Collections

@ConsistentCopyVisibility
data class FlowBehavior private constructor(
    val pinned: Boolean,
    val group: String?,
    val annotated: Boolean,
    val actions: List<FlowAction>,
    val states: List<String>,
) {
    init {
        if ((group != null && !Regex("[a-z0-9]{1,16}").matches(group)) || actions.size > 20 || states.size > 20 ||
            states.any { it.length > 50 }
        ) {
            throw InvalidDocumentException("invalid flow behavior")
        }
    }

    fun withoutPlacementControls(): FlowBehavior = copy(pinned = false, group = null)

    companion object {
        fun of(
            controls: FlowControls,
            actions: List<FlowAction>,
            states: List<String>,
        ): FlowBehavior =
            FlowBehavior(
                controls.pinned,
                controls.group,
                controls.annotated,
                Collections.unmodifiableList(actions.toList()),
                Collections.unmodifiableList(states.toList()),
            )
    }
}
