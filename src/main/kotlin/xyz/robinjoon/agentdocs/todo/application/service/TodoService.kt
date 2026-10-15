package xyz.robinjoon.agentdocs.todo.application.service

import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.todo.application.port.input.TodoNotFoundException
import xyz.robinjoon.agentdocs.todo.application.port.input.TodoUseCase
import xyz.robinjoon.agentdocs.todo.application.port.output.TodoRepository
import xyz.robinjoon.agentdocs.todo.domain.todo.Todo
import xyz.robinjoon.agentdocs.todo.domain.todo.TodoId
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Transactional(readOnly = true)
class TodoService(
    private val repository: TodoRepository,
    private val clock: Clock,
) : TodoUseCase {
    @Transactional
    override fun create(title: String): Todo = repository.add(Todo.create(TodoId(UUID.randomUUID()), title, Instant.now(clock)))

    override fun get(id: TodoId): Todo = repository.findById(id) ?: throw TodoNotFoundException(id)

    override fun list(): List<Todo> = repository.findAll()

    @Transactional
    override fun complete(id: TodoId): Todo {
        val todo = get(id)
        return if (todo.completed) todo else repository.update(todo.complete())
    }
}
