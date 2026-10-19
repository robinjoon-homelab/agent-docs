package xyz.robinjoon.agentdocs.access.application.port.input

import xyz.robinjoon.agentdocs.access.application.model.CodeGrant
import xyz.robinjoon.agentdocs.access.application.model.RefreshGrant
import xyz.robinjoon.agentdocs.access.application.model.TokenSet

// The token endpoint. Failures are OAuthException with the standard error code.
interface TokenUseCase {
    // Redeems a code once and creates the connection the owner approved.
    fun exchange(grant: CodeGrant): TokenSet

    // Rotates both tokens; the old refresh token stops working.
    fun refresh(grant: RefreshGrant): TokenSet
}
