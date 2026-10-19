package xyz.robinjoon.agentdocs.workspace.domain.document.history

import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentHeader

// The summary filled in when a writer leaves it empty, such as "블록 1개 추가, 2개 수정".
object ChangeSummary {
    fun of(
        before: DocumentHeader,
        after: DocumentHeader,
        changes: List<BlockChange>,
        reordered: Boolean = false,
    ): String {
        val parts = headerParts(before, after) + blockParts(changes) + listOfNotNull("블록 순서 변경".takeIf { reordered })
        return parts.joinToString(", ").ifEmpty { "변경 없음" }
    }

    private fun headerParts(
        before: DocumentHeader,
        after: DocumentHeader,
    ): List<String> =
        listOfNotNull(
            "제목 변경".takeIf { before.title != after.title },
            "경로 변경".takeIf { before.path != after.path },
            "상태 변경".takeIf { before.properties.status != after.properties.status },
            "태그 변경".takeIf { before.properties.tags != after.properties.tags },
            "담당자 변경".takeIf { before.properties.assignee != after.properties.assignee },
        )

    private fun blockParts(changes: List<BlockChange>): List<String> {
        val counts = changes.groupingBy { it.kind }.eachCount()
        val words = mapOf(BlockChangeKind.ADDED to "추가", BlockChangeKind.MODIFIED to "수정", BlockChangeKind.REMOVED to "삭제")
        val parts = BlockChangeKind.entries.mapNotNull { kind -> counts[kind]?.let { "${it}개 ${words.getValue(kind)}" } }
        return if (parts.isEmpty()) emptyList() else listOf("블록 " + parts.joinToString(", "))
    }
}
