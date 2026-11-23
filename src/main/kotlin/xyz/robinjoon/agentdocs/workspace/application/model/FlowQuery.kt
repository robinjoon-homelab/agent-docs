package xyz.robinjoon.agentdocs.workspace.application.model

// Narrows a deployed flow document to the screens and elements an agent can name without knowing block ids.
// text matches parts a person sees or writes; kind is "screen", "element" or an element kind; screen is a screen ID such
// as M-01 or a screen block id. Matches come in document order; offset and limit page through them.
data class FlowQuery(
    val text: String? = null,
    val kind: String? = null,
    val screen: String? = null,
    val limit: Int = DEFAULT_LIMIT,
    val offset: Int = 0,
) {
    init {
        if (text != null && (text.isBlank() || text.length > MAX_TEXT)) {
            throw InvalidRequestException("flow query text must be 1 to $MAX_TEXT characters")
        }
        if (kind != null && kind.isBlank()) throw InvalidRequestException("flow query kind must not be blank")
        if (screen != null && screen.isBlank()) throw InvalidRequestException("flow query screen must not be blank")
        if (limit !in 1..MAX_LIMIT) throw InvalidRequestException("flow query limit must be 1 to $MAX_LIMIT")
        if (offset < 0) throw InvalidRequestException("flow query offset must not be negative")
    }

    companion object {
        const val DEFAULT_LIMIT = 20
        const val MAX_LIMIT = 100
        const val MAX_TEXT = 100
    }
}
