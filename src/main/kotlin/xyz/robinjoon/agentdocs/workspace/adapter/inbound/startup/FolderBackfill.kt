package xyz.robinjoon.agentdocs.workspace.adapter.inbound.startup

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.scheduling.annotation.Scheduled
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentUseCase
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.document.PathTakenException

// Documents written before paths had parent documents left folders that no document holds. Each start turns those
// folders into empty documents in the owner's name; one that finds none adds nothing. Only the count is logged,
// since paths are people's data.
class FolderBackfill(
    private val documents: DocumentUseCase,
    private val owner: () -> Access?,
) : ApplicationRunner {
    private var completed = false

    override fun run(args: ApplicationArguments) = backfill()

    @Scheduled(fixedDelayString = "PT30S", initialDelayString = "PT30S")
    @Synchronized
    fun backfill() {
        if (completed) return
        val confirmed = owner() ?: return
        try {
            val created = documents.fillMissingParents(confirmed)
            completed = true
            if (created.isNotEmpty()) log.info("Turned {} folders into documents", created.size)
        } catch (exception: PathTakenException) {
            log.warn("Folders were not turned into documents; the next pass retries", exception)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(FolderBackfill::class.java)
    }
}
