package xyz.robinjoon.agentdocs.access.application.port.input

import xyz.robinjoon.agentdocs.access.application.model.Caller
import xyz.robinjoon.agentdocs.access.application.model.GitHubIdentity

interface SignInUseCase {
    // Records the person and returns who they are for the session. Fails with SignInRefusedException when the
    // account is not in the instance allowlist.
    fun signIn(identity: GitHubIdentity): Caller
}
