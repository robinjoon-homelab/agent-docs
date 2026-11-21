package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp

// Sent with initialize. Clients may show only the first part, so it starts with the essentials and
// stays well under 4,096 characters.
internal object McpInstructions {
    const val TEXT =
        "Agent Docs keeps your user's documents as blocks in spaces. Find your space with spaces_resolve (pass `git remote get-url " +
            "origin`) " +
            "or spaces_list. A space may name an agent guide, the document with its writing rules, terms, and templates: both tools " +
            "return it as agentGuide, so read it with docs_read before you write in that space. Read with docs_read: every block " +
            "is preceded by <!-- b:ID h:HASH -->. Change documents with docs_edit, " +
            "naming each " +
            "block id and its current hash as ifHash; if someone changed the block since you read it, nothing is applied and the error " +
            "returns " +
            "the current block, so read it again and retry. Never overwrite a person's change. When you rewrite a table, keep its " +
            "<!--r:ID--> row markers and either copy its <!-- b:ID h:HASH cols:... --> line exactly as read or leave that line " +
            "out, so comments on cells stay attached.\n\n" +
            "Work arrives as comment threads, not calls: when your user runs you, use comments_query (open threads by default) to see " +
            "what " +
            "people asked, with the anchored block and its neighbors. After handling a thread, pass answering=<thread id> to docs_edit " +
            "so the " +
            "revision is linked, then comments_write action reply with markAnswered true. You cannot resolve a thread a person " +
            "opened.\n\n" +
            "Screen flows use type flow and settings, screen, element blocks with the same b:ID/h:HASH comments. " +
            "Use block IDs for edits; deleting or moving a screen carries its elements. Replace settings to change frame/idPrefix. " +
            "Flow comment suggestions replace desc; comments_query reports target ok/changed/elementDeleted/screenDeleted. " +
            "Images: upload with docs_upload first and always give alt text. For a local file, call it with no content and run the curl " +
            "command it returns; write out contentBase64 only without a shell and only up to about 5KB, because long base64 gets " +
            "mistyped. A hand-drawn diagram is ![alt](asset:SVG \"excalidraw asset:SCENE\"); read its SVG or text elements with " +
            "docs_upload assetId and document, and add includeSource for the scene JSON. To change one, keep every scene field you " +
            "did not change, upload the new .excalidraw scene and its matching .svg, and replace the same block id with both new " +
            "ids. Use opId on writes you might retry. Document links are " +
            "[title](doc:ID). Do not put secrets in documents or comments."
}
