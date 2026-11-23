package xyz.robinjoon.agentdocs.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.design.JsonFlowDesignValidator
import xyz.robinjoon.agentdocs.workspace.application.port.output.FlowDesignValidator

@Configuration(proxyBeanMethods = false)
class FlowDesignConfig {
    @Bean
    fun flowDesignValidator(json: ObjectMapper): FlowDesignValidator = JsonFlowDesignValidator(json)
}
