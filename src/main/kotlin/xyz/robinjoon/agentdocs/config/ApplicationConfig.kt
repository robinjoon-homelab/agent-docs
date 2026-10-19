package xyz.robinjoon.agentdocs.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration(proxyBeanMethods = false)
class ApplicationConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
