package xyz.robinjoon.agentdocs.application.port.output

import xyz.robinjoon.agentdocs.application.model.ActivityRecord
import java.time.Instant

// Appends to the shared activity log inside the caller's transaction, so an entry exists exactly when its change
// committed.
interface ActivityReporter {
    fun record(record: ActivityRecord)

    // Removes entries older than the retention limit and returns how many were removed.
    fun pruneBefore(cutoff: Instant): Int
}
