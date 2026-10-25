package xyz.robinjoon.agentdocs.config

import xyz.robinjoon.agentdocs.access.adapter.inbound.web.GitHubUserService
import xyz.robinjoon.agentdocs.access.adapter.inbound.web.SessionCookieFilter
import xyz.robinjoon.agentdocs.access.adapter.inbound.web.SignInSuccessHandler
import xyz.robinjoon.agentdocs.access.adapter.inbound.web.SignOutHandler

// The web chain's sign-in pieces, which share one cookie setting. Held here rather than as beans for the same reason as
// SecurityComponents: a Filter bean would also run in the servlet chain.
class WebSignInComponents(
    val cookieFilter: SessionCookieFilter,
    val users: GitHubUserService,
    val success: SignInSuccessHandler,
    val signOut: SignOutHandler,
)
