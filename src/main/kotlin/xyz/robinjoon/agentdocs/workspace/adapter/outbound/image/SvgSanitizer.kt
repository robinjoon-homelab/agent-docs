package xyz.robinjoon.agentdocs.workspace.adapter.outbound.image

import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.SAXException
import xyz.robinjoon.agentdocs.workspace.domain.asset.InvalidAssetException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerException
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

// Parses an SVG without DTDs or external entities and drops everything that can run code or load other content:
// script and embedding elements, event handler attributes, and links that are not fragments or inline images.
// Uploaded files are also served with a sandbox CSP, so this is the first of two barriers, not the only one.
class SvgSanitizer {
    // Returns null for bytes that do not look like an SVG at all, so the caller can report an unsupported type.
    // Bytes that look like an SVG but do not parse as one are rejected.
    fun sanitize(bytes: ByteArray): ByteArray? {
        if (!looksLikeSvg(bytes)) return null
        val document =
            try {
                builderFactory().newDocumentBuilder().parse(ByteArrayInputStream(bytes))
            } catch (exception: SAXException) {
                throw InvalidAssetException("the SVG could not be parsed", exception)
            } catch (exception: IOException) {
                throw InvalidAssetException("the SVG could not be read", exception)
            }
        val root = document.documentElement
        if (root.localName != "svg" || root.namespaceURI != SVG_NAMESPACE) {
            throw InvalidAssetException("an SVG needs an svg root element in the SVG namespace")
        }
        clean(root)
        return serialize(document)
    }

    private fun clean(element: Element) {
        val children = (0 until element.childNodes.length).map { element.childNodes.item(it) }
        children.forEach { child ->
            when {
                child.nodeType == Node.PROCESSING_INSTRUCTION_NODE -> element.removeChild(child)
                child is Element && child.localName?.lowercase() in blockedElements -> element.removeChild(child)
                child is Element -> clean(child)
            }
        }
        val attributes = (0 until element.attributes.length).map { element.attributes.item(it) }
        attributes.filterIsInstance<Attr>().filter { unsafe(it.localName ?: it.name, it.value) }.forEach(element::removeAttributeNode)
    }

    private fun unsafe(
        name: String,
        value: String,
    ): Boolean {
        val lower = name.lowercase()
        val target = value.trim().lowercase()
        return lower.startsWith("on") ||
            (lower == "href" && !(target.startsWith("#") || target.startsWith("data:image/"))) ||
            (lower == "style" && (target.contains("url(") || target.contains("expression(") || target.contains("javascript:")))
    }

    private fun serialize(document: Document): ByteArray =
        try {
            val out = ByteArrayOutputStream()
            val factory = TransformerFactory.newInstance()
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            val transformer = factory.newTransformer()
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
            transformer.transform(DOMSource(document), StreamResult(out))
            out.toByteArray()
        } catch (exception: TransformerException) {
            throw IllegalStateException("a parsed SVG could not be written back", exception)
        }

    // Past an optional byte order mark and whitespace the file starts with markup and mentions an svg tag early on.
    private fun looksLikeSvg(bytes: ByteArray): Boolean {
        val head = String(bytes, 0, minOf(bytes.size, SNIFF_LENGTH), Charsets.UTF_8).trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        return head.startsWith("<") && head.contains("<svg")
    }

    private fun builderFactory(): DocumentBuilderFactory =
        try {
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isXIncludeAware = false
                isExpandEntityReferences = false
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            }
        } catch (exception: ParserConfigurationException) {
            throw IllegalStateException("the XML parser cannot be made safe for SVG", exception)
        }

    private companion object {
        const val SVG_NAMESPACE = "http://www.w3.org/2000/svg"
        const val SNIFF_LENGTH = 1024

        // animate and set can rewrite an attribute such as href to a script URL after the attributes were cleaned.
        val blockedElements =
            setOf("script", "foreignobject", "iframe", "object", "embed", "audio", "video", "handler", "listener", "animate", "set")
    }
}
