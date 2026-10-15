package xyz.robinjoon.agentdocs.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import xyz.robinjoon.agentdocs.todo.adapter.outbound.persistence.ExposedTodoRepository
import xyz.robinjoon.agentdocs.todo.application.port.output.TodoRepository

@Configuration(proxyBeanMethods = false)
class PersistenceConfig {
    @Bean
    fun todoRepository(): TodoRepository = ExposedTodoRepository()
}
