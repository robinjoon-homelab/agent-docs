package xyz.robinjoon.agentdocs.todo.application.port.input

import xyz.robinjoon.agentdocs.todo.domain.todo.Todo
import xyz.robinjoon.agentdocs.todo.domain.todo.TodoId

interface TodoUseCase {
    fun create(title: String): Todo

    fun get(id: TodoId): Todo

    fun list(): List<Todo>

    fun complete(id: TodoId): Todo
}
