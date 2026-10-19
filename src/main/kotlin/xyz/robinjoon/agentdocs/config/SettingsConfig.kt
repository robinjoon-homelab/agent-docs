package xyz.robinjoon.agentdocs.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AgentDocsProperties::class)
class SettingsConfig
