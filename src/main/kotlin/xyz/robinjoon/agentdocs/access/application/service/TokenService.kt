package xyz.robinjoon.agentdocs.access.application.service

import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.access.application.model.CodeGrant
import xyz.robinjoon.agentdocs.access.application.model.OAuthException
import xyz.robinjoon.agentdocs.access.application.model.RefreshGrant
import xyz.robinjoon.agentdocs.access.application.model.TokenSet
import xyz.robinjoon.agentdocs.access.application.port.input.TokenUseCase
import xyz.robinjoon.agentdocs.access.application.port.output.AuthorizationRequestRepository
import xyz.robinjoon.agentdocs.access.application.port.output.ConnectionRepository
import xyz.robinjoon.agentdocs.access.application.port.output.OAuthClientRepository
import xyz.robinjoon.agentdocs.access.application.port.output.SecretSource
import xyz.robinjoon.agentdocs.access.domain.TokenHash
import xyz.robinjoon.agentdocs.access.domain.authorization.AuthorizationFailedException
import xyz.robinjoon.agentdocs.access.domain.authorization.CodeExchange
import xyz.robinjoon.agentdocs.access.domain.connection.ClientRef
import xyz.robinjoon.agentdocs.access.domain.connection.Connection
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionId
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionKind
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionOwner
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionState
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionTerm
import xyz.robinjoon.agentdocs.access.domain.connection.Credentials
import java.time.Duration
import java.time.Instant

// Access tokens last an hour and refresh tokens rotate, both bounded by the connection's own expiry. Every token is
// issued for this server's MCP endpoint only.
@Transactional(readOnly = true)
class TokenService(
    private val requests: AuthorizationRequestRepository,
    private val connections: ConnectionRepository,
    private val clients: OAuthClientRepository,
    private val secrets: SecretSource,
    private val journal: AccessJournal,
    private val policy: TokenPolicy,
) : TokenUseCase {
    @Transactional
    override fun exchange(grant: CodeGrant): TokenSet {
        val clientId = required(grant.clientId, "client_id")
        val exchange = codeExchange(grant, clientId)
        policy.requireResource(grant.resources)
        val now = journal.now()
        val request = requests.findByCodeForUpdate(exchange.codeHash) ?: throw OAuthException("invalid_grant", "the code is unknown")
        val (exchanged, approval) =
            try {
                request.exchange(exchange, now)
            } catch (exception: AuthorizationFailedException) {
                throw OAuthException(exception.error, exception.message.orEmpty(), cause = exception)
            }
        val delegator = policy.requireOwner(approval.userId)
        requests.update(exchanged)
        val client = clients.find(clientId) ?: throw OAuthException("invalid_client", "unknown client_id", OAuthException.UNAUTHORIZED)
        val owner = ConnectionOwner(approval.userId, ConnectionKind.AGENT, ClientRef(client.clientId, client.metadata.name, client.host))
        val term = ConnectionTerm(now, now.plus(approval.lifetime), null, now)
        val (tokens, credentials) = issueTokens(now)
        // A code exchange creates at most one connection. Reuse its request ID so the approval log also identifies it.
        val connection =
            connections.add(
                Connection.issue(ConnectionId(request.id.value), owner, approval.grant, ConnectionState(term, credentials)),
            )
        val caller = Callers.agent(connection, delegator.account.login)
        journal.recordConnection(caller, "connection.issued", "OAuth 연결 발급: ${client.host ?: client.metadata.name}", connection.id.value)
        return tokens.withScopeOf(connection, now)
    }

    @Transactional
    override fun refresh(grant: RefreshGrant): TokenSet {
        val token = required(grant.refreshToken, "refresh_token")
        policy.requireResource(grant.resources)
        val now = journal.now()
        val connection =
            connections.findByRefreshHashForUpdate(TokenHash.of(token))?.takeIf { it.isActiveAt(now) }
                ?: throw OAuthException("invalid_grant", "the refresh token is unknown, already used, or its connection ended")
        policy.requireOwner(connection.owner.userId)
        if (grant.clientId != null && grant.clientId != connection.owner.client.clientId) {
            throw OAuthException("invalid_grant", "the refresh token was issued to another client")
        }
        val (tokens, credentials) = issueTokens(now)
        val rotated = connections.update(connection.rotate(credentials, now))
        return tokens.withScopeOf(rotated, now)
    }

    private fun codeExchange(
        grant: CodeGrant,
        clientId: String,
    ): CodeExchange =
        CodeExchange(
            clientId,
            TokenHash.of(required(grant.code, "code")),
            required(grant.redirectUri, "redirect_uri"),
            required(grant.verifier, "code_verifier"),
        )

    private fun issueTokens(now: Instant): Pair<RawTokens, Credentials> {
        val (access, accessHash) = Tokens.next(secrets, Tokens.ACCESS)
        val (refresh, refreshHash) = Tokens.next(secrets, Tokens.REFRESH)
        return RawTokens(access, refresh) to Credentials(accessHash, now.plus(accessLifetime), refreshHash)
    }

    private fun required(
        value: String?,
        name: String,
    ): String = value?.takeIf(String::isNotBlank) ?: throw OAuthException("invalid_request", "$name is required")

    private class RawTokens(
        val access: String,
        val refresh: String,
    ) {
        fun withScopeOf(
            connection: Connection,
            now: Instant,
        ): TokenSet =
            TokenSet(access, refresh, Duration.between(now, connection.credentials.accessExpiresAt).seconds, connection.grant.scope.wire)
    }

    private companion object {
        val accessLifetime: Duration = Duration.ofHours(1)
    }
}
