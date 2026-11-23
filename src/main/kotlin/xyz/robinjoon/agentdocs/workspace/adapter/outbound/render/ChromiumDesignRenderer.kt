package xyz.robinjoon.agentdocs.workspace.adapter.outbound.render

import tools.jackson.core.type.TypeReference
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import xyz.robinjoon.agentdocs.workspace.application.model.DesignRenderInput
import xyz.robinjoon.agentdocs.workspace.application.model.DesignRenderResult
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.RenderUnavailableException
import xyz.robinjoon.agentdocs.workspace.application.port.output.FlowDesignRenderer

class ChromiumDesignRenderer(
    private val binary: String,
    private val json: ObjectMapper,
) : FlowDesignRenderer {
    private val page by lazy { RenderPage(json, resource("renderer.js"), resource("renderer.css")) }

    override fun render(input: DesignRenderInput): DesignRenderResult {
        val html = page.html(input)
        return ChromiumSession.run(binary, json) { browser ->
            load(browser, html)
            val result = measurements(browser)
            val capture = result.path("capture")
            val png = if (input.selection.image && capture.isObject) screenshot(browser, capture) else null
            DesignRenderResult(json.convertValue(result, object : TypeReference<Map<String, Any?>>() {}), png)
        }
    }

    private fun resource(name: String): String =
        javaClass.getResourceAsStream("/static/design-render/$name")?.bufferedReader()?.use { it.readText() }
            ?: throw RenderUnavailableException("The installed application is missing design renderer $name")

    private fun load(
        browser: ChromiumSession,
        html: String,
    ) {
        val id = browser.call("Target.createTarget", mapOf("url" to "about:blank")).path("targetId").asString()
        browser.session = browser.call("Target.attachToTarget", mapOf("targetId" to id, "flatten" to true)).path("sessionId").asString()
        browser.call("Page.enable")
        browser.call("Network.enable")
        browser.call(
            "Network.emulateNetworkConditions",
            mapOf(
                "offline" to true,
                "latency" to 0,
                "downloadThroughput" to 0,
                "uploadThroughput" to 0,
            ),
        )
        val frame =
            browser
                .call("Page.getFrameTree")
                .path("frameTree")
                .path("frame")
                .path("id")
                .asString()
        browser.call("Page.setDocumentContent", mapOf("frameId" to frame, "html" to html))
    }

    private fun measurements(browser: ChromiumSession): JsonNode {
        val options = mapOf("expression" to WAIT_FOR_RENDER, "returnByValue" to true, "awaitPromise" to true)
        val value = browser.call("Runtime.evaluate", options).path("result").path("value")
        if (!value.isString || value.stringValue().length > 1_000_000) throw RenderUnavailableException("Renderer returned no measurements")
        val result = json.readTree(value.stringValue())
        if (!result.isObject) throw RenderUnavailableException("Invalid renderer measurements")
        if (result.hasNonNull("error")) throw InvalidRequestException("Design render: " + result.path("error").asString())
        return result
    }

    private fun screenshot(
        browser: ChromiumSession,
        bounds: JsonNode,
    ): String {
        val clip =
            listOf("x", "y", "width", "height").associateWith {
                bounds.path(it).takeIf(JsonNode::isNumber)?.doubleValue() ?: throw InvalidRequestException("No visible area to capture")
            }
        val width = clip.getValue("width")
        val height = clip.getValue("height")
        if (width <= 0 || height <= 0 || width > 8192 || height > 8192 || width * height > 8_000_000) {
            throw InvalidRequestException("Choose a smaller render target; PNG must fit in 8192 px per side and 8 megapixels")
        }
        val options = mapOf("format" to "png", "captureBeyondViewport" to true, "clip" to (clip + ("scale" to 1)))
        return browser.call("Page.captureScreenshot", options).path("data").asString()
    }

    private companion object {
        const val WAIT_FOR_RENDER = """new Promise((resolve, reject) => {
            const until = performance.now() + 16000;
            function read() {
              const node = document.querySelector('textarea#design-result');
              if (node && node.value) return resolve(node.value);
              if (performance.now() >= until) return reject(new Error('render timeout'));
              setTimeout(read, 20);
            }
            read();
          })"""
    }
}
