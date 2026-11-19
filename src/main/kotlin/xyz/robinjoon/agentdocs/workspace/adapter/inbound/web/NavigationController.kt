package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import xyz.robinjoon.agentdocs.workspace.application.port.input.NavigationUseCase
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import java.security.Principal
import java.util.UUID

@RestController
class NavigationController(
    private val navigation: NavigationUseCase,
) {
    @GetMapping("/api/navigation/mobile-target")
    fun mobileTarget(
        @RequestParam space: String,
        @RequestParam path: String,
        principal: Principal?,
    ): Map<String, Any>? {
        val target = navigation.mobileTarget(CallerAccess.of(principal), space, path) ?: return null
        return mapOf("id" to target.id.value, "version" to target.version, "blocks" to target.blocks.map(BlockJson::write))
    }

    @GetMapping("/api/navigation/recent")
    fun recent(
        @RequestParam(defaultValue = "20") limit: Int,
        principal: Principal?,
    ) = NavigationJson.page(navigation.recent(CallerAccess.of(principal), limit))

    @GetMapping("/api/navigation/favorites")
    fun favorites(
        @RequestParam(defaultValue = "20") limit: Int,
        principal: Principal?,
    ) = NavigationJson.page(navigation.favorites(CallerAccess.of(principal), limit))

    @PostMapping("/api/navigation/documents/{id}/view")
    fun view(
        @PathVariable id: UUID,
        @RequestParam(required = false) opId: String?,
        principal: Principal?,
    ): ResponseEntity<Unit> {
        navigation.recordView(CallerAccess.write(principal, opId, null), DocumentId(id))
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/api/navigation/documents/{id}/favorite")
    fun favoriteStatus(
        @PathVariable id: UUID,
        principal: Principal?,
    ) = mapOf("enabled" to navigation.isFavorite(CallerAccess.of(principal), DocumentId(id)))

    @PutMapping("/api/navigation/documents/{id}/favorite")
    fun favorite(
        @PathVariable id: UUID,
        @RequestBody body: FavoriteRequest,
        principal: Principal?,
    ): ResponseEntity<Unit> {
        navigation.favorite(CallerAccess.write(principal, body.opId, null), DocumentId(id), body.enabled)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/api/navigation/documents/{id}/backlinks")
    fun backlinks(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "20") limit: Int,
        principal: Principal?,
    ) = NavigationJson.page(navigation.backlinks(CallerAccess.of(principal), DocumentId(id), limit))
}

data class FavoriteRequest(
    val enabled: Boolean,
    val opId: String? = null,
)
