package xyz.robinjoon.agentdocs.config

import xyz.robinjoon.agentdocs.access.adapter.inbound.web.ApiAuthenticationEntryPoint
import xyz.robinjoon.agentdocs.access.adapter.inbound.web.BearerTokenFilter
import xyz.robinjoon.agentdocs.access.adapter.inbound.web.RateLimitFilter
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.UploadGateFilter

// The filters both security chains share. They are held here rather than registered as beans, because Spring Boot
// would also add a Filter bean to the servlet chain and run it twice.
class SecurityComponents(
    val bearer: BearerTokenFilter,
    val uploadGate: UploadGateFilter,
    val rateLimit: RateLimitFilter,
    val entryPoint: ApiAuthenticationEntryPoint,
)
