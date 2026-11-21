package xyz.robinjoon.agentdocs.workspace.domain.document

// Omitted markdown attributes keep the stored values on replace, like omitted x/y; repeated fields are replaced as sent.
object FlowOmitted {
    private val frameFields = setOf("frame", "frameW", "frameH")

    fun keep(
        target: FlowScreen,
        next: FlowScreen,
        defaults: FlowDefaults,
    ): FlowScreen =
        next.copy(
            screenId = pick(defaults.screenId, target.screenId, next.screenId),
            entry = pick("entry" in defaults.omitted, target.entry, next.entry),
            frame = pick(defaults.omitted.containsAll(frameFields), target.frame, next.frame),
        )

    fun keep(
        target: FlowElement,
        next: FlowElement,
        defaults: FlowDefaults,
    ): FlowElement =
        next.copy(
            appearance = appearance(target.appearance, next.appearance, defaults.omitted),
            text = next.text.copy(value = pick("value" in defaults.omitted, target.text.value, next.text.value)),
            behavior = behavior(target.behavior, next.behavior, defaults.omitted),
            placement =
                next.placement.copy(
                    x = pick(defaults.x, target.placement.x, next.placement.x),
                    y = pick(defaults.y, target.placement.y, next.placement.y),
                ),
        )

    private fun appearance(
        target: FlowAppearance,
        next: FlowAppearance,
        omitted: Set<String>,
    ): FlowAppearance =
        if (target.kind != next.kind) {
            next
        } else {
            FlowAppearance.of(
                next.kind,
                pick("look" in omitted, target.look, next.look),
                next.items,
                pick("hasDate" in omitted, target.date, next.date),
            )
        }

    private fun behavior(
        target: FlowBehavior,
        next: FlowBehavior,
        omitted: Set<String>,
    ): FlowBehavior =
        FlowBehavior.of(
            FlowControls(
                pick("pinned" in omitted, target.pinned, next.pinned),
                pick("group" in omitted, target.group, next.group),
                next.annotated,
            ),
            next.actions,
            next.states,
        )

    private fun <T> pick(
        keepStored: Boolean,
        stored: T,
        sent: T,
    ): T = if (keepStored) stored else sent
}
