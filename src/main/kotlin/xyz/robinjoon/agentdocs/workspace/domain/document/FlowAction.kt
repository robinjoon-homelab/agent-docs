package xyz.robinjoon.agentdocs.workspace.domain.document

data class FlowAction(
    val whenText: String,
    val to: String? = null,
    val mode: String = "same",
) {
    init {
        if (whenText.length > 100 || mode !in modes || (to != null && !Regex("[A-Z0-9]{0,4}-[0-9]{2,}").matches(to))) {
            throw InvalidDocumentException("invalid flow action")
        }
    }

    companion object {
        private val modes = setOf("same", "page", "sheet", "sheetClose", "popup", "newWindow", "error")
    }
}
