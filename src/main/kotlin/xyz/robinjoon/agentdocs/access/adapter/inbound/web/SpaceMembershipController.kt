package xyz.robinjoon.agentdocs.access.adapter.inbound.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import xyz.robinjoon.agentdocs.access.application.model.MembershipInvite
import xyz.robinjoon.agentdocs.access.application.model.MembershipView
import xyz.robinjoon.agentdocs.access.application.port.input.SpaceMembershipUseCase
import xyz.robinjoon.agentdocs.access.domain.InvalidAccessException
import xyz.robinjoon.agentdocs.access.domain.membership.SpaceMembershipId
import xyz.robinjoon.agentdocs.access.domain.membership.SpaceRole
import java.net.URI
import java.security.Principal
import java.time.Instant
import java.util.UUID

// Space memberships for the owner's space settings. Spaces are named by id only; the web app joins their names from
// the space list, as it does for connections.
@RestController
@RequestMapping("/api/memberships")
class SpaceMembershipController(
    private val memberships: SpaceMembershipUseCase,
) {
    @GetMapping
    fun list(
        @RequestParam(required = false) space: String?,
        principal: Principal?,
    ): List<MembershipResponse> {
        val caller = Callers.of(principal)
        return memberships.list(caller, spaceOf(space)).map(MembershipResponse::from)
    }

    @PostMapping
    fun invite(
        @RequestBody request: InviteRequest,
        principal: Principal?,
    ): ResponseEntity<MembershipResponse> {
        val caller = Callers.of(principal)
        val invite =
            MembershipInvite(
                spaceOf(request.space),
                request.login ?: throw InvalidAccessException("login is required"),
                roleOf(request.role),
            )
        val created = MembershipResponse.from(memberships.invite(caller, invite))
        return ResponseEntity.created(URI.create("/api/memberships/${created.id}")).body(created)
    }

    @PatchMapping("/{id}")
    fun changeRole(
        @PathVariable id: UUID,
        @RequestBody request: RoleRequest,
        principal: Principal?,
    ): MembershipResponse {
        val caller = Callers.of(principal)
        return MembershipResponse.from(memberships.changeRole(caller, SpaceMembershipId(id), roleOf(request.role)))
    }

    @DeleteMapping("/{id}")
    fun revoke(
        @PathVariable id: UUID,
        principal: Principal?,
    ): ResponseEntity<Unit> {
        memberships.revoke(Callers.of(principal), SpaceMembershipId(id))
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build()
    }

    // Read as text, so a missing or malformed id answers with the same invalid problem as the other fields.
    private fun spaceOf(value: String?): UUID {
        val text = value?.trim() ?: throw InvalidAccessException("space is required")
        if (!uuidPattern.matches(text)) throw InvalidAccessException("space must be a space id")
        return UUID.fromString(text)
    }

    private fun roleOf(value: String?): SpaceRole =
        value?.let(SpaceRole::of) ?: throw InvalidAccessException("role must be editor or commenter")

    private companion object {
        val uuidPattern = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    }
}

data class InviteRequest(
    val space: String?,
    val login: String?,
    val role: String?,
)

data class RoleRequest(
    val role: String?,
)

// Built from the view rather than field by field, so the response stays one flat object. A pending invitation has no
// user yet; once joined, login follows the member's current GitHub login.
class MembershipResponse private constructor(
    view: MembershipView,
) {
    val id: UUID = view.membership.id.value
    val space: UUID = view.membership.spaceId
    val login: String = view.login
    val avatarUrl: String = "https://github.com/${view.login}.png"
    val role: String = view.membership.role.wire
    val state: String = if (view.membership.isActive) "active" else "pending"
    val userId: UUID? = view.membership.member?.value
    val invitedAt: Instant = view.membership.invitation.invitedAt
    val joinedAt: Instant? = view.membership.state.joinedAt

    companion object {
        fun from(view: MembershipView): MembershipResponse = MembershipResponse(view)
    }
}
