package xyz.robinjoon.agentdocs.workspace.adapter.inbound.startup

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentUseCase
import xyz.robinjoon.agentdocs.workspace.domain.document.PathTakenException

// Documents written before paths had parent documents left folders that no document holds. Each start turns those
// folders into empty documents in the owner's name; one that finds none adds nothing. Only the count is logged,
// since paths are people's data.
class FolderBackfill(
    private val documents: DocumentUseCase,
    private val owner: String,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        try {
            val created = documents.fillMissingParents(owner)
            if (created.isNotEmpty()) log.info("Turned {} folders into documents", created.size)
        } catch (exception: PathTakenException) {
            // A request on the instance still running took a folder path first. Nothing was written; the next start
            // tries again, and the app works meanwhile because a folder without a document still shows in the tree.
            log.warn("Folders were not turned into documents; the next start retries", exception)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(FolderBackfill::class.java)
    }
}
