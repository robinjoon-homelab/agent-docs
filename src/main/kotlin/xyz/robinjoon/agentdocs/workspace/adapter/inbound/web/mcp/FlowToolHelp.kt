package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp

internal object FlowToolHelp {
    const val SYNTAX =
        "Flow blocks keep <!-- b:ID h:HASH --> annotations. Settings: ## flowSettings then frame, idPrefix, nextScreen " +
            "as '- key: value' lines. Screen: ## M-01 Name then entry, frame, frameW, " +
            "frameH. Element: ### Label · kind (append · 모양만 " +
            "when unannotated), then value, look, hasDate, date, x, y, w, h, pinned, " +
            "group; repeat item/state and when/to/mode triples. " +
            "Descriptions follow attributes after a blank line. Values only escape backslash/newline/CR/tab; " +
            "whitespace-only values use Unicode escapes. Label delimiters use backslash-m; descriptions escape leading syntax only. " +
            "Hyphens in targets stay literal (to: M-05). Empty frame/group/look/to means " +
            "null; hasDate preserves null versus empty dates. " +
            "A heading starts each block. Elements need w and h; other attributes may be omitted: inserts use defaults " +
            "(empty text, the kind's default look, no date, unpinned, ungrouped, document frame) and replace keeps stored values. " +
            "Repeated item/state/when-to-mode lines are replaced as sent. Parse errors name the failing heading. " +
            "Empty screen ID (two spaces after ##) allocates the next number on insertion. " +
            "Screen frame note is a text page: its description (up to 20000 characters) shows as wide formatted text " +
            "and it holds no elements. " +
            "Descriptions render a markdown subset: blank-line paragraphs, - and 1. lists, | tables, bold, italic, code and links. " +
            "An image element whose value is exactly asset:ID (upload with docs_upload first) shows that image; its label is the alt text. "
    const val EDIT =
        "Flow edits use blockId, never screenId aliases. Screen delete/move carries its elements; " +
            "the last screen cannot " +
            "be deleted. Screen insert/move targets another screen before/after, or start/end; " +
            "after means after its elements and start " +
            "means after settings. Element insert targets after a screen or " +
            "before/after an element; element move stays in the same screen. " +
            "This preserves screen ownership. On insert, missing x/y use the frame center and " +
            "height/3 (bar: top). Duplicate screen IDs are numbered " +
            "again. On replace, an empty screen ID and omitted attributes, including x/y, keep the existing values. " +
            "Replace settings to change frame/idPrefix; docs_move only changes " +
            "document metadata. replaceText uses original text, searching screen names/descriptions and element labels/descriptions " +
            "and element visible text. warnings contain blockId/code: missingDescription, missingScreen, outsideFrame. "
}
