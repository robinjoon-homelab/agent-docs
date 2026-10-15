package xyz.robinjoon.agentdocs.todo.application.port.input

import xyz.robinjoon.agentdocs.todo.domain.todo.TodoId

class TodoNotFoundException(
    id: TodoId,
) : RuntimeException("Todo not found: $id")
