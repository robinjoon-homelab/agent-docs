package xyz.robinjoon.agentdocs.application.model

import java.time.Instant

// A read of every retained entry from a moment on, for a reader that reconciles the whole range again instead of
// trusting an id cursor: ids are taken before commit, so a smaller id can still appear after a larger one was read.
// It matches entries of the listed kinds and, when createdComments is set, every entry that created a comment. afterId
// pages through one pass in id order.
data class ActivityScan(
    val since: Instant,
    val kinds: Set<String>,
    val createdComments: Boolean,
    val afterId: Long?,
    val limit: Int,
)
