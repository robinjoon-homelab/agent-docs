package xyz.robinjoon.agentdocs.webhook.adapter.inbound.web

import org.springframework.security.core.Authentication
import xyz.robinjoon.agentdocs.access.application.model.Caller
import xyz.robinjoon.agentdocs.webhook.application.model.WebhookOperator
import java.security.Principal

// Turns the caller the access context authenticated into what webhook use cases decide on.
internal object WebhookOperators {
    fun of(principal: Principal?): WebhookOperator {
        val caller = (principal as? Authentication)?.principal as? Caller ?: throw WebhookUnauthenticatedException()
        return WebhookOperator(caller.userId, caller.login, caller.owner, caller.agent != null)
    }
}

class WebhookUnauthenticatedException : RuntimeException("sign in to manage webhooks")
