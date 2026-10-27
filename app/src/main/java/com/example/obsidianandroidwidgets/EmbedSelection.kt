package com.example.obsidianandroidwidgets

internal object EmbedSelection {
    fun select(markdown: String, fragment: String): String {
        if (fragment.isBlank()) return markdown
        val lines = markdown.lines()
        var fence = ""
        val literal = lines.map { line ->
            val marker = Regex("^\\s*(`{3,}|~{3,})(.*)$").find(line)
            val before = fence.isNotEmpty()
            if (marker != null) {
                val value = marker.groupValues[1]
                if (fence.isEmpty()) fence = value
                else if (fence[0] == value[0] && value.length >= fence.length && marker.groupValues[2].isBlank()) fence = ""
            }
            before || marker != null
        }
        if (fragment.startsWith('^')) {
            val id = Regex("(?:^|\\s)${Regex.escape(fragment)}\\s*$")
            val at = lines.indices.firstOrNull { !literal[it] && id.containsMatchIn(lines[it]) } ?: return "Referenced block not found."
            var start = at
            var end = at + 1
            if (lines[at].trim() == fragment) {
                start--
                while (start >= 0 && lines[start].isBlank()) start--
                while (start > 0 && lines[start - 1].isNotBlank()) start--
            } else if (Regex("^\\s*(?:[-*+]|\\d+[.)])\\s").containsMatchIn(lines[at])) {
                val indent = lines[at].takeWhile { it.isWhitespace() }.length
                while (end < lines.size && lines[end].isNotBlank() && lines[end].takeWhile { it.isWhitespace() }.length > indent) end++
            } else {
                while (start > 0 && lines[start - 1].isNotBlank()) start--
                while (end < lines.size && lines[end].isNotBlank()) end++
            }
            return lines.subList(start.coerceAtLeast(0), end).joinToString("\n").replace(id, "").trim()
        }
        fun clean(value: String) = value.replace(Regex("[*_`~=]"), "").trim().lowercase()
        val wanted = fragment.split('#').map(::clean)
        val stack = mutableListOf<Pair<Int, String>>()
        var start = -1
        var level = 0
        for (i in lines.indices) {
            if (literal[i]) continue
            val match = Regex("^ {0,3}(#{1,6})\\s+(.+?)(?:\\s+#+)?\\s*$").matchEntire(lines[i]) ?: continue
            val depth = match.groupValues[1].length
            if (start >= 0 && depth <= level) return lines.subList(start, i).joinToString("\n").trimEnd()
            while (stack.lastOrNull()?.first?.let { it >= depth } == true) stack.removeAt(stack.lastIndex)
            stack += depth to clean(match.groupValues[2])
            if (stack.map { it.second }.takeLast(wanted.size) == wanted) { start = i; level = depth }
        }
        return if (start >= 0) lines.drop(start).joinToString("\n").trimEnd() else "Referenced heading not found."
    }
}
