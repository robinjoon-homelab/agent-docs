package xyz.robinjoon.agentdocs.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper
import xyz.robinjoon.agentdocs.access.adapter.outbound.http.HttpClientMetadataSource
import xyz.robinjoon.agentdocs.access.application.model.AccessSettings
import xyz.robinjoon.agentdocs.access.application.port.input.AuthorizationUseCase
import xyz.robinjoon.agentdocs.access.application.port.input.CallerUseCase
import xyz.robinjoon.agentdocs.access.application.port.input.ClientRegistrationUseCase
import xyz.robinjoon.agentdocs.access.application.port.input.ConnectionUseCase
import xyz.robinjoon.agentdocs.access.application.port.input.EmergencyLoginUseCase
import xyz.robinjoon.agentdocs.access.application.port.input.SignInUseCase
import xyz.robinjoon.agentdocs.access.application.port.input.SpaceMembershipUseCase
import xyz.robinjoon.agentdocs.access.application.port.input.TokenUseCase
import xyz.robinjoon.agentdocs.access.application.port.input.WebSessionUseCase
import xyz.robinjoon.agentdocs.access.application.service.AccessJournal
import xyz.robinjoon.agentdocs.access.application.service.AuthorizationService
import xyz.robinjoon.agentdocs.access.application.service.CallerService
import xyz.robinjoon.agentdocs.access.application.service.ClientDirectory
import xyz.robinjoon.agentdocs.access.application.service.ClientRegistrationService
import xyz.robinjoon.agentdocs.access.application.service.ConnectionService
import xyz.robinjoon.agentdocs.access.application.service.EmergencyLoginService
import xyz.robinjoon.agentdocs.access.application.service.SignInService
import xyz.robinjoon.agentdocs.access.application.service.SpaceMembershipService
import xyz.robinjoon.agentdocs.access.application.service.TokenPolicy
import xyz.robinjoon.agentdocs.access.application.service.TokenService
import xyz.robinjoon.agentdocs.access.application.service.WebSessionService
import xyz.robinjoon.agentdocs.access.domain.user.Allowlist
import xyz.robinjoon.agentdocs.application.port.output.ActivityReporter
import java.time.Clock

@Configuration(proxyBeanMethods = false)
class AccessConfig {
    @Bean
    fun accessPorts(): AccessPorts = AccessPorts()

    // Fails at startup when agent-docs.owner is empty, since nobody could sign in.
    @Bean
    fun accessSettings(properties: AgentDocsProperties): AccessSettings =
        AccessSettings(properties.owner, properties.members.toSet(), properties.publicUrl.trimEnd('/'))

    @Bean
    fun accessJournal(
        activity: ActivityReporter,
        clock: Clock,
    ): AccessJournal = AccessJournal(activity, clock)

    @Bean
    fun clientDirectory(
        ports: AccessPorts,
        json: ObjectMapper,
        clock: Clock,
        settings: AccessSettings,
    ): ClientDirectory = ClientDirectory(ports.clients, HttpClientMetadataSource(json), ports.secrets, clock, settings.issuer)

    @Bean
    fun signInUseCase(
        ports: AccessPorts,
        settings: AccessSettings,
        journal: AccessJournal,
    ): SignInUseCase = SignInService(ports.users, ports.memberships, Allowlist(settings.owner, settings.members), journal)

    @Bean
    fun webSessionUseCase(
        ports: AccessPorts,
        settings: AccessSettings,
        clock: Clock,
    ): WebSessionUseCase =
        WebSessionService(ports.sessions, ports.users, ports.memberships, Allowlist(settings.owner, settings.members), ports.secrets, clock)

    @Bean
    fun spaceMembershipUseCase(
        ports: AccessPorts,
        settings: AccessSettings,
        journal: AccessJournal,
    ): SpaceMembershipUseCase = SpaceMembershipService(ports.memberships, ports.users, Allowlist(settings.owner, settings.members), journal)

    @Bean
    fun emergencyLoginUseCase(
        ports: AccessPorts,
        sessions: WebSessionUseCase,
        settings: AccessSettings,
        clock: Clock,
    ): EmergencyLoginUseCase =
        EmergencyLoginService(
            ports.emergency,
            ports.users,
            ports.secrets,
            sessions,
            settings,
            clock,
        )

    @Bean
    fun callerUseCase(
        ports: AccessPorts,
        settings: AccessSettings,
        clock: Clock,
    ): CallerUseCase =
        CallerService(
            ports.connections,
            ports.users,
            clock,
            Allowlist(settings.owner, settings.members),
            settings.issuer.trimEnd('/') + "/oauth/clients/adocs",
        )

    @Bean
    fun connectionUseCase(
        ports: AccessPorts,
        journal: AccessJournal,
    ): ConnectionUseCase = ConnectionService(ports.connections, ports.secrets, journal)

    @Bean
    fun authorizationUseCase(
        ports: AccessPorts,
        directory: ClientDirectory,
        journal: AccessJournal,
        settings: AccessSettings,
    ): AuthorizationUseCase = AuthorizationService(ports.requests, ports.clients, directory, ports.secrets, journal, settings)

    @Bean
    fun tokenUseCase(
        ports: AccessPorts,
        journal: AccessJournal,
        settings: AccessSettings,
    ): TokenUseCase =
        TokenService(ports.requests, ports.connections, ports.clients, ports.secrets, journal, TokenPolicy(ports.users, settings))

    @Bean
    fun clientRegistrationUseCase(directory: ClientDirectory): ClientRegistrationUseCase = ClientRegistrationService(directory)
}
