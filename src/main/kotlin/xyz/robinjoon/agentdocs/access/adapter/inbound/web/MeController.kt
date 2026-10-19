package xyz.robinjoon.agentdocs.access.adapter.inbound.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal
import java.util.UUID

// Who the web app is signed in as. The avatar is GitHub's public avatar for the login, so no GitHub token is needed.
@RestController
class MeController {
    @GetMapping("/api/me")
    fun me(principal: Principal?): MeResponse {
        val caller = Callers.of(principal)
        return MeResponse(caller.userId, caller.login, caller.owner, "https://github.com/${caller.login}.png", caller.agent?.clientName)
    }
}

data class MeResponse(
    val userId: UUID,
    val login: String,
    val owner: Boolean,
    val avatarUrl: String,
    val agent: String?,
)
