package xyz.robinjoon.agentdocs.workspace.domain.document

data class FlowFrame(
    val preset: String? = null,
    val width: Int? = null,
    val height: Int? = null,
) {
    init {
        val valid =
            if (preset == "custom") {
                width in 200..3000 && height in 200..3000
            } else {
                (preset == null || preset in presets) && width == null && height == null
            }
        if (!valid) throw InvalidDocumentException("invalid flow frame")
    }

    fun width(default: String): Int = width ?: requireNotNull(presets[preset ?: default]).first

    fun height(default: String): Int = height ?: requireNotNull(presets[preset ?: default]).second

    companion object {
        val presets =
            mapOf(
                "phone" to (390 to 844),
                "phoneSmall" to (360 to 800),
                "tabletP" to (820 to 1180),
                "tabletL" to (1180 to 820),
                "laptop" to (1280 to 800),
                "desktop" to (1440 to 900),
                "modal" to (480 to 560),
            )
    }
}
