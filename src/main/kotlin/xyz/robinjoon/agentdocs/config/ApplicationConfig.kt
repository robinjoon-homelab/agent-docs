package xyz.robinjoon.agentdocs.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import xyz.robinjoon.agentdocs.todo.application.port.input.TodoUseCase
import xyz.robinjoon.agentdocs.todo.application.port.output.TodoRepository
import xyz.robinjoon.agentdocs.todo.application.service.TodoService
import java.time.Clock

@Configuration(proxyBeanMethods = false)
class ApplicationConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun todoUseCase(
        repository: TodoRepository,
        clock: Clock,
    ): TodoUseCase = TodoService(repository, clock)
}
