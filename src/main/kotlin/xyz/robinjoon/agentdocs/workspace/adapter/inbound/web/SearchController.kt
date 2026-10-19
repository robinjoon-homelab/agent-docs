package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import xyz.robinjoon.agentdocs.workspace.application.port.input.SearchUseCase
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import java.security.Principal

@RestController
@RequestMapping("/api/search")
class SearchController(
    private val search: SearchUseCase,
) {
    @GetMapping
    fun search(
        @RequestParam q: String,
        @RequestParam(required = false) space: String?,
        principal: Principal?,
    ): List<Map<String, Any?>> =
        search.search(CallerAccess.of(principal), q, space?.let(::SpaceSlug)).map { hit ->
            mapOf(
                "kind" to hit.kind.name.lowercase(),
                "space" to WorkspaceJson.spaceRef(hit.space),
                "documentId" to hit.document.id.value,
                "path" to hit.document.path.value,
                "title" to hit.document.title,
                "blockId" to hit.blockId?.value,
                "threadId" to hit.threadId?.value,
                "snippet" to hit.snippet,
            )
        }

    @GetMapping("/titles")
    fun titles(
        @RequestParam q: String,
        principal: Principal?,
    ): List<Map<String, Any?>> =
        search.titles(CallerAccess.of(principal), q).map { (space, document) ->
            mapOf(
                "space" to WorkspaceJson.spaceRef(space),
                "documentId" to document.id.value,
                "path" to document.path.value,
                "title" to document.title,
            )
        }
}
