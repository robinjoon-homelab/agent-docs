package xyz.robinjoon.agentdocs.workspace.domain.document

internal typealias WireFields = Map<String, Any?>

internal object LegacyWireComponents {
    val fill = mapOf("x" to mapOf("mode" to "fill"), "y" to mapOf("mode" to "fill"))

    fun key(kind: String): String = "wire:$kind"

    fun definition(kind: String): WireFields =
        buildMap {
            put("id", key(kind))
            put("kind", "component")
            put("name", "기본 · ${labels.getValue(kind)}")
            put("props", properties(kind))
            put("root", LegacyWireContent.root(kind))
            val variants = LegacyWireLooks.variants(kind)
            if (variants.isNotEmpty()) put("variants", variants)
        }

    fun inputs(element: FlowElement): WireFields =
        buildMap {
            put("label", element.text.label)
            put("value", element.text.value)
            element.appearance.look?.let { put("look", it) }
            element.appearance.items?.let { rows ->
                put("items", rows.mapIndexed { index, item -> mapOf("itemKey" to "row-$index", "fields" to mapOf("text" to item)) })
            }
            if (element.appearance.kind == "path") {
                element.appearance.date?.let { put("date", it) }
                put("dateLayout", if (element.appearance.date.isNullOrEmpty()) "undated" else "dated")
            }
        }

    private fun properties(kind: String): List<WireFields> =
        buildList {
            add(prop("label", "이름", "text"))
            add(prop("value", "표시할 글자", "text"))
            LegacyWireLooks.choices[kind]?.let { choices ->
                add(
                    prop("look", "모양", "choice") +
                        mapOf("choices" to choices.map { mapOf("id" to it, "name" to it) }, "initial" to choices.first()),
                )
            }
            when (kind) {
                "list" -> {
                    add(prop("items", "항목", "list") + ("fields" to listOf(prop("text", "글자", "text"))))
                }

                "image" -> {
                    add(prop("picture", "이미지", "slot"))
                }

                "path" -> {
                    add(prop("date", "날짜", "text"))
                    val options = listOf("undated", "dated").map { mapOf("id" to it, "name" to it) }
                    add(prop("dateLayout", "날짜 표시", "choice") + mapOf("choices" to options, "initial" to "undated"))
                }
            }
        }

    private fun prop(
        id: String,
        name: String,
        type: String,
    ): WireFields = mapOf("id" to id, "name" to name, "type" to type)

    private val labels =
        mapOf(
            "title" to "제목",
            "text" to "텍스트",
            "field" to "입력칸",
            "select" to "선택",
            "area" to "여러 줄 입력",
            "button" to "버튼",
            "link" to "링크",
            "list" to "목록",
            "option" to "선택 항목",
            "notice" to "알림",
            "path" to "값 표시",
            "bar" to "상단 바",
            "crumb" to "위치 표시",
            "block" to "본문 블록",
            "box" to "사각형",
            "image" to "이미지 자리",
        )
}
