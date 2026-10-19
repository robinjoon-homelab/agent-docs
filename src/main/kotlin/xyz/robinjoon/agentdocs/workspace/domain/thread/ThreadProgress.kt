package xyz.robinjoon.agentdocs.workspace.domain.thread

import xyz.robinjoon.agentdocs.workspace.domain.Actor
import java.time.Instant
import java.util.Collections

// The state of the conversation: its status, the revisions written in answer to it, and when it last changed.
@ConsistentCopyVisibility
data class ThreadProgress private constructor(
    val status: ThreadStatus,
    val linkedRevisions: List<Int>,
    val timeline: ThreadTimeline,
) {
    fun moved(
        status: ThreadStatus,
        at: Instant,
    ): ThreadProgress {
        val resolution = if (status == ThreadStatus.RESOLVED) timeline else timeline.copy(resolvedAt = null, resolvedBy = null)
        return ThreadProgress(status, linkedRevisions, resolution.copy(updatedAt = at))
    }

    fun resolved(
        by: Actor,
        at: Instant,
    ): ThreadProgress = ThreadProgress(ThreadStatus.RESOLVED, linkedRevisions, ThreadTimeline(timeline.createdAt, at, at, by))

    fun linked(
        revision: Int,
        at: Instant,
    ): ThreadProgress = ThreadProgress(status, Collections.unmodifiableList(linkedRevisions + revision), timeline.copy(updatedAt = at))

    fun touched(at: Instant): ThreadProgress = copy(timeline = timeline.copy(updatedAt = at))

    companion object {
        fun opened(at: Instant): ThreadProgress = ThreadProgress(ThreadStatus.OPEN, emptyList(), ThreadTimeline(at, at, null, null))

        fun restore(
            status: ThreadStatus,
            linkedRevisions: List<Int>,
            timeline: ThreadTimeline,
        ): ThreadProgress = ThreadProgress(status, Collections.unmodifiableList(linkedRevisions.toList()), timeline)
    }
}
