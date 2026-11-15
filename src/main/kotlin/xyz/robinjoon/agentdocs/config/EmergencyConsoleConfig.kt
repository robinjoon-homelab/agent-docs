package xyz.robinjoon.agentdocs.config

import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import xyz.robinjoon.agentdocs.access.adapter.inbound.console.EmergencyLoginCommand
import xyz.robinjoon.agentdocs.access.adapter.outbound.console.FileEmergencyLinkDelivery
import xyz.robinjoon.agentdocs.access.application.model.AccessSettings
import xyz.robinjoon.agentdocs.access.application.port.input.EmergencyLoginIssueUseCase
import xyz.robinjoon.agentdocs.access.application.service.EmergencyLoginService
import xyz.robinjoon.agentdocs.access.application.service.WebSessionService
import xyz.robinjoon.agentdocs.access.domain.user.Allowlist
import java.nio.file.Path
import java.time.Clock

@Configuration(proxyBeanMethods = false)
@Profile("emergency-console")
@EnableAutoConfiguration
@EnableConfigurationProperties(AgentDocsProperties::class)
class EmergencyConsoleConfig {
    @Bean
    fun emergencyIssuer(properties: AgentDocsProperties): EmergencyLoginIssueUseCase {
        val ports = AccessPorts()
        val settings = AccessSettings(properties.owner, properties.members.toSet(), properties.publicUrl)
        val clock = Clock.systemUTC()
        val sessions = WebSessionService(ports.sessions, ports.users, Allowlist(settings.owner, settings.members), ports.secrets, clock)
        return EmergencyLoginService(ports.emergency, ports.users, ports.secrets, sessions, settings, clock)
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            require(args.size == 1) { "Supply a new private output file path" }
            SpringApplicationBuilder(EmergencyConsoleConfig::class.java)
                .web(WebApplicationType.NONE)
                .profiles("emergency-console")
                .properties("spring.main.keep-alive=false", "spring.main.banner-mode=off")
                .run("--spring.main.keep-alive=false", "--spring.main.banner-mode=off")
                .use { context ->
                    val link = EmergencyLoginCommand(context.getBean(EmergencyLoginIssueUseCase::class.java)).execute()
                    FileEmergencyLinkDelivery(Path.of(args.single())).deliver(link)
                }
        }
    }
}
