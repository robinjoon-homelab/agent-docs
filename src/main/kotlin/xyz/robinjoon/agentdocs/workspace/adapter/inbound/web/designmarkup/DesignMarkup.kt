package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.designmarkup

import org.jsoup.nodes.Element
import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.DesignJsonReader
import xyz.robinjoon.agentdocs.workspace.domain.document.DesignJsonWriter
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowDesignBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.design.DesignAddress
import java.math.BigDecimal

internal data class AuthorTarget(
    val kind: String,
    val name: String,
    val id: String?,
    val props: List<String>,
    val parent: DesignAddress? = null,
) {
    init {
        if (kind !in setOf("screen", "component", "layer")) invalid("Expected screen, component or layer")
        if ((kind == "layer") != (parent != null)) invalid("Only a layer needs its parent address")
        if (kind != "layer" && name.isBlank()) invalid("Expected a nonempty design name")
        if (kind == "layer" && (id != null || name.isNotEmpty())) invalid("Use data-key on the layer; omit name and designId")
        id?.let(::identifier)
        if (kind != "component" && props.isNotEmpty()) invalid("Only components declare props")
        if (props.distinct().size != props.size) invalid("Duplicate component property")
    }
}

private data class Definition(
    val id: String,
    val name: String,
    val props: List<Fields>,
    val root: Element,
) {
    companion object {
        fun read(e: Element): Definition =
            e.located {
                e.only("data-component", "data-name", "data-props")
                val id = identifier(e.attr("data-component"))
                val props =
                    e
                        .attr("data-props")
                        .trim()
                        .split(Regex("\\s+"))
                        .filter(String::isNotEmpty)
                if (props.distinct().size != props.size) invalid("Duplicate component property")
                Definition(
                    id,
                    e.optional("data-name") ?: id,
                    textProps(props),
                    e.layers().singleOrNull() ?: invalid("Expected one template root"),
                )
            }
    }
}

private fun textProps(names: List<String>): List<Fields> = names.map { mapOf("id" to identifier(it), "name" to it, "type" to "text") }

internal class DesignMarkup(
    blocks: List<Block>,
) {
    private val designs = blocks.filterIsInstance<FlowDesignBlock>().toMutableList()
    private val settings = designs.firstOrNull()?.takeIf { it.kind == "settings" } ?: invalid("Open a v4 flow document first")
    private val components = designs.filter { it.kind == "component" }.associateBy { it.designId }
    private val tokens = designs.find { it.kind == "tokens" }

    init {
        if (designs.size != blocks.size) invalid("Open a v4 flow document first")
    }

    fun create(
        source: String,
        target: AuthorTarget,
    ): List<FlowDesignBlock> {
        val elements = MarkupGrammar.read(source)
        val sheets = elements.filter { it.tagName() == "style" }
        val templates = elements.filter { it.tagName() == "template" }.map(Definition::read)
        val root = elements.singleOrNull { it.tagName() !in setOf("template", "style") } ?: invalid("Expected one root box")
        val library = MarkupLibrary(tokens?.fields, sheets)
        val resources = resources(library, templates)
        if (library.changed) saveLibrary(library)
        templates.forEach { template -> template.root.located { addTemplate(template, resources) } }
        val parent = target.parent
        if (parent != null) {
            val owner = designs.singleOrNull { it.designId == parent.owner } ?: invalid("Unknown insertion owner")
            designs[designs.indexOf(owner)] = MarkupInsertion(owner, parent).append(root, resources)
            return designs
        }
        val props = textProps(target.props)
        val fields = ownedRoot(root, resources, props) + mapOf("kind" to target.kind, "name" to target.name)
        val more = if (target.kind == "screen") screenFields() else mapOf("props" to props)
        add(fields + more, target.id)
        return designs
    }

    private fun resources(
        library: MarkupLibrary,
        templates: List<Definition>,
    ): MarkupResources {
        val definitions = components.mapValues { objects(it.value.fields["props"]).associateBy { p -> p.key() } }.toMutableMap()
        if (templates.distinctBy { it.id }.size != templates.size) invalid("Duplicate template ID")
        templates.forEach { definitions[it.id] = it.props.associateBy { prop -> prop.key() } }
        return MarkupResources(library.colors, library.styles.mapTo(mutableSetOf()) { it.key() }, definitions)
    }

    private fun ownedRoot(
        root: Element,
        resources: MarkupResources,
        props: List<Fields>,
    ): Fields =
        root.located {
            val layer = MarkupLayers(resources, props.mapTo(mutableSetOf()) { it.key() }, root).build(root)
            if (layer["kind"] != "box") invalid("Screen and component roots must be boxes")
            mapOf("root" to layer)
        }

    private fun addTemplate(
        template: Definition,
        resources: MarkupResources,
    ) {
        val fields =
            mapOf("kind" to "component", "id" to template.id, "name" to template.name, "props" to template.props) +
                ownedRoot(template.root, resources, template.props)
        val existing = components[template.id]
        if (existing == null) {
            add(fields, template.id)
        } else if (existing.fields !=
            DesignJsonReader(DesignJsonWriter.write(fields)).read()
        ) {
            invalid("Existing component differs: " + template.id)
        }
    }

    private fun saveLibrary(library: MarkupLibrary) {
        val fields = library.fields(tokens?.fields)
        if (tokens == null) {
            add(fields, null)
        } else {
            designs[designs.indexOf(tokens)] = tokens.withFields(fields)
        }
    }

    private fun screenFields(): Fields {
        val ordinal = settings.fields["nextScreen"] as? BigDecimal ?: invalid("Missing nextScreen setting")
        if (ordinal.signum() <= 0 || ordinal.stripTrailingZeros().scale() > 0) invalid("Invalid nextScreen setting")
        var number = ordinal.toBigIntegerExact()
        val prefix = settings.string("idPrefix").orEmpty().ifEmpty { "S" }
        val codes = designs.map { it.string("code") }.toSet()
        var code: String
        do {
            code = prefix + "-" + number.toString().padStart(2, '0')
            number++
        } while (code in codes)
        designs[0] = settings.withFields(settings.fields + ("nextScreen" to number))
        return mapOf("code" to code)
    }

    private fun add(
        fields: Fields,
        requested: String?,
    ) {
        val id = BlockId.next()
        val key = requested ?: id.value
        if (designs.any { it.designId == key }) invalid("Design ID already in use: $key")
        designs.add(FlowDesignBlock.parse(id, DesignJsonWriter.write(fields + ("id" to key))))
    }
}
