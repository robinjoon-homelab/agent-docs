package xyz.robinjoon.agentdocs.access.application.service

import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.access.application.model.AccessSettings
import xyz.robinjoon.agentdocs.access.application.model.ApprovalRequest
import xyz.robinjoon.agentdocs.access.application.model.AuthorizationStart
import xyz.robinjoon.agentdocs.access.application.model.AuthorizationView
import xyz.robinjoon.agentdocs.access.application.model.AuthorizeRequest
import xyz.robinjoon.agentdocs.access.application.model.Caller
import xyz.robinjoon.agentdocs.access.application.model.ClientMetadataUnavailableException
import xyz.robinjoon.agentdocs.access.application.model.OAuthException
import xyz.robinjoon.agentdocs.access.application.port.input.AuthorizationRequestNotFoundException
import xyz.robinjoon.agentdocs.access.application.port.input.AuthorizationUseCase
import xyz.robinjoon.agentdocs.access.application.port.input.OwnerOnlyException
import xyz.robinjoon.agentdocs.access.application.port.output.AuthorizationRequestRepository
import xyz.robinjoon.agentdocs.access.application.port.output.OAuthClientRepository
import xyz.robinjoon.agentdocs.access.application.port.output.SecretSource
import xyz.robinjoon.agentdocs.access.domain.authorization.Approval
import xyz.robinjoon.agentdocs.access.domain.authorization.AuthorizationAsk
import xyz.robinjoon.agentdocs.access.domain.authorization.AuthorizationRequest
import xyz.robinjoon.agentdocs.access.domain.authorization.AuthorizationRequestId
import xyz.robinjoon.agentdocs.access.domain.authorization.PkceChallenge
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionScope
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionTerm
import xyz.robinjoon.agentdocs.access.domain.connection.Grant
import xyz.robinjoon.agentdocs.access.domain.user.UserId
import java.util.UUID

@Transactional(readOnly = true)
class AuthorizationService(
    private val requests: AuthorizationRequestRepository,
    private val clients: OAuthClientRepository,
    private val directory: ClientDirectory,
    private val secrets: SecretSource,
    private val journal: AccessJournal,
    private val settings: AccessSettings,
) : AuthorizationUseCase {
    // Problems found before the redirect URI is trusted are shown on an error page; later ones go back to the client.
    @Transactional
    override fun begin(request: AuthorizeRequest): AuthorizationStart =
        try {
            start(request)
        } catch (exception: OAuthException) {
            AuthorizationStart.Rejected(exception.message.orEmpty())
        } catch (exception: ClientMetadataUnavailableException) {
            AuthorizationStart.Rejected(exception.message.orEmpty())
        }

    override fun describe(
        caller: Caller,
        id: AuthorizationRequestId,
    ): AuthorizationView {
        // A read-only transaction cannot lock rows on PostgreSQL, and the screen decides nothing.
        val request = pending(caller, id, requests::findById)
        val client = clients.find(request.ask.clientId) ?: throw AuthorizationRequestNotFoundException(id)
        return AuthorizationView(request, client)
    }

    @Transactional
    override fun approve(
        caller: Caller,
        id: AuthorizationRequestId,
        approval: ApprovalRequest,
    ): String {
        val request = pending(caller, id, requests::findByIdForUpdate)
        val (code, hash) = Tokens.next(secrets, Tokens.CODE)
        val lifetime = ConnectionTerm.lifetimeOf(approval.lifetimeDays)
        val approved =
            requests.update(
                request.approve(Approval(UserId(caller.userId), Grant.of(approval.spaces, approval.scope), lifetime, hash), journal.now()),
            )
        val client = clients.find(request.ask.clientId)
        journal.record(caller, "connection.approved", "연결 승인: ${client?.host ?: client?.metadata?.name ?: request.ask.clientId}")
        return OAuthRedirects.build(
            approved.ask.redirectUri,
            listOf("code" to code, "state" to approved.ask.state, "iss" to settings.issuer),
        )
    }

    @Transactional
    override fun deny(
        caller: Caller,
        id: AuthorizationRequestId,
    ): String {
        val request = pending(caller, id, requests::findByIdForUpdate)
        val denied = requests.update(request.deny(journal.now()))
        return OAuthRedirects.build(
            denied.ask.redirectUri,
            errorParameters("access_denied" to "the owner denied the connection", denied.ask.state),
        )
    }

    private fun start(request: AuthorizeRequest): AuthorizationStart {
        val clientId = request.clientId ?: return AuthorizationStart.Rejected("client_id is missing")
        val client = directory.resolve(clientId)
        val redirect =
            request.redirectUri?.takeIf(client::allowsRedirect)
                ?: return AuthorizationStart.Rejected("redirect_uri is not registered for this client")
        val problem = problemOf(request)
        if (problem != null) {
            return AuthorizationStart.RedirectWithError(OAuthRedirects.build(redirect, errorParameters(problem, request.state)))
        }
        val scope =
            request.target.scope
                ?.split(' ')
                ?.mapNotNull(ConnectionScope::of)
                ?.maxOrNull()
        val ask =
            AuthorizationAsk(
                clientId,
                redirect,
                request.state,
                PkceChallenge(requireNotNull(request.pkce.challenge)),
                settings.resource,
                scope,
            )
        val started = requests.add(AuthorizationRequest.start(AuthorizationRequestId(UUID.randomUUID()), ask, journal.now()))
        return AuthorizationStart.Started(started.id)
    }

    private fun pending(
        caller: Caller,
        id: AuthorizationRequestId,
        find: (AuthorizationRequestId) -> AuthorizationRequest?,
    ): AuthorizationRequest {
        if (!caller.owner || caller.agent != null) throw OwnerOnlyException("approving a connection")
        return find(id)?.takeIf { it.isOpenAt(journal.now()) } ?: throw AuthorizationRequestNotFoundException(id)
    }

    // The first problem with the request as an OAuth error code and description, or null when there is none.
    private fun problemOf(request: AuthorizeRequest): Pair<String, String>? {
        val challenge = request.pkce.challenge
        val foreign =
            request.target.resources
                .map { it.trimEnd('/') }
                .filter { it != settings.resource }
        return when {
            request.responseType != "code" -> "unsupported_response_type" to "response_type must be code"
            challenge == null || request.pkce.method != "S256" -> "invalid_request" to "PKCE with code_challenge_method S256 is required"
            !challengePattern.matches(challenge) -> "invalid_request" to "code_challenge must be 43 base64url characters"
            foreign.isNotEmpty() -> "invalid_target" to "resource must be ${settings.resource}"
            else -> null
        }
    }

    private fun errorParameters(
        problem: Pair<String, String>,
        state: String?,
    ): List<Pair<String, String?>> =
        listOf(
            "error" to problem.first,
            "error_description" to problem.second,
            "state" to state,
            "iss" to settings.issuer,
        )

    private companion object {
        val challengePattern = Regex("[A-Za-z0-9_-]{43}")
    }
}
