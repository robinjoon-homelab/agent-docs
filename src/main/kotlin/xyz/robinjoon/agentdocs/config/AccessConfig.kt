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
import xyz.robinjoon.agentdocs.access.application.port.input.SignInUseCase
import xyz.robinjoon.agentdocs.access.application.port.input.TokenUseCase
import xyz.robinjoon.agentdocs.access.application.service.AccessJournal
import xyz.robinjoon.agentdocs.access.application.service.AuthorizationService
import xyz.robinjoon.agentdocs.access.application.service.CallerService
import xyz.robinjoon.agentdocs.access.application.service.ClientDirectory
import xyz.robinjoon.agentdocs.access.application.service.ClientRegistrationService
import xyz.robinjoon.agentdocs.access.application.service.ConnectionService
import xyz.robinjoon.agentdocs.access.application.service.SignInService
import xyz.robinjoon.agentdocs.access.application.service.TokenService
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
    ): ClientDirectory = ClientDirectory(ports.clients, HttpClientMetadataSource(json), ports.secrets, clock)

    @Bean
    fun signInUseCase(
        ports: AccessPorts,
        settings: AccessSettings,
        clock: Clock,
    ): SignInUseCase = SignInService(ports.users, Allowlist(settings.owner, settings.members), clock)

    @Bean
    fun callerUseCase(
        ports: AccessPorts,
        clock: Clock,
    ): CallerUseCase = CallerService(ports.connections, ports.users, clock)

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
    ): TokenUseCase = TokenService(ports.requests, ports.connections, ports.clients, ports.secrets, journal, settings)

    @Bean
    fun clientRegistrationUseCase(directory: ClientDirectory): ClientRegistrationUseCase = ClientRegistrationService(directory)
}
