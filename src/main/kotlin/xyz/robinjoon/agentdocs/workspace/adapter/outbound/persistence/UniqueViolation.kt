package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.exceptions.ExposedSQLException

// PostgreSQL and H2 both report a unique constraint violation as SQLSTATE 23505.
internal fun ExposedSQLException.isUniqueViolation(): Boolean = sqlState == "23505"
