package xyz.robinjoon.agentdocs.workspace.adapter.outbound.drawing

import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import xyz.robinjoon.agentdocs.workspace.application.model.DrawingText
import xyz.robinjoon.agentdocs.workspace.application.model.VerifiedScene
import xyz.robinjoon.agentdocs.workspace.application.port.output.SceneVerifier
import xyz.robinjoon.agentdocs.workspace.domain.asset.InvalidAssetException

// Reads an Excalidraw scene as @excalidraw/excalidraw 0.18.1 writes it and refuses what the editor would render
// unsafely when someone opens it: AI-generated HTML frames (iframe elements run their HTML in a frame), element types
// this version does not know, repeated element ids, and image files given as addresses, which the browser would fetch
// on open. Links on elements are kept as data; the editor opens them only when clicked. The bytes are never rewritten.
class JsonSceneVerifier(
    private val json: ObjectMapper,
) : SceneVerifier {
    override fun verify(bytes: ByteArray): VerifiedScene {
        val root = parse(bytes)
        if (!root.isObject || root.path("type").textOrNull() != SCENE_TYPE) {
            throw InvalidAssetException("a drawing scene must be Excalidraw JSON with \"type\": \"$SCENE_TYPE\"")
        }
        val elements =
            root.get("elements")?.takeIf(JsonNode::isArray)
                ?: throw InvalidAssetException("a drawing scene needs an \"elements\" array")
        val visible = visibleElements(elements.values().toList())
        return VerifiedScene(visible.size, checkedFileCount(root.get("files")), visible.mapNotNull(::textOf))
    }

    private fun parse(bytes: ByteArray): JsonNode {
        val text = bytes.toString(Charsets.UTF_8)
        if (!text.toByteArray(Charsets.UTF_8).contentEquals(bytes)) throw InvalidAssetException("a drawing scene must be UTF-8 JSON")
        return try {
            json.readTree(text)
        } catch (exception: JacksonException) {
            throw InvalidAssetException("a drawing scene must be valid JSON", exception)
        }
    }

    private fun visibleElements(elements: List<JsonNode>): List<JsonNode> {
        val ids = HashSet<String>()
        elements.forEach { element ->
            val id =
                element.path("id").textOrNull()?.takeIf(String::isNotEmpty)
                    ?: throw InvalidAssetException("every drawing element needs an id")
            if (element.path("type").textOrNull() !in elementTypes) throw InvalidAssetException("drawing element $id has an unknown type")
            if (!ids.add(id)) throw InvalidAssetException("drawing element id $id appears more than once")
        }
        val visible = elements.filterNot { it.path("isDeleted").let { flag -> flag.isBoolean && flag.booleanValue() } }
        if (visible.any { it.path("type").textOrNull() == AI_FRAME }) {
            throw InvalidAssetException("a drawing with AI-generated HTML frames (iframe elements) cannot be stored")
        }
        return visible
    }

    private fun checkedFileCount(files: JsonNode?): Int {
        if (files == null || files.isNull) return 0
        if (!files.isObject) throw InvalidAssetException("drawing files must be an object keyed by file id")
        val entries = files.properties()
        entries.forEach { (fileId, file) ->
            val dataUrl = file.path("dataURL").textOrNull()
            if (dataUrl == null || !dataUrl.startsWith(EMBEDDED)) {
                throw InvalidAssetException("drawing file $fileId must be embedded as a data: URL; an address would be fetched on open")
            }
        }
        return entries.size
    }

    private fun textOf(element: JsonNode): DrawingText? {
        if (element.path("type").textOrNull() != "text") return null
        val text = element.path("originalText").textOrNull() ?: element.path("text").textOrNull() ?: return null
        return DrawingText(element.path("id").stringValue(), text, element.path("containerId").textOrNull())
    }

    private fun JsonNode.textOrNull(): String? = takeIf(JsonNode::isString)?.stringValue()

    private companion object {
        const val SCENE_TYPE = "excalidraw"
        const val AI_FRAME = "iframe"
        const val EMBEDDED = "data:"

        // Every element type of @excalidraw/excalidraw 0.18.1 (packages/excalidraw/element/types.ts).
        val elementTypes =
            setOf(
                "selection",
                "rectangle",
                "diamond",
                "ellipse",
                "text",
                "line",
                "arrow",
                "freedraw",
                "image",
                "frame",
                "magicframe",
                "embeddable",
                "iframe",
            )
    }
}
