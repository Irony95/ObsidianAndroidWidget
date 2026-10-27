package com.example.obsidianandroidwidgets

import java.security.MessageDigest

data class NoteTask(val line: Int, val originalLine: String, val markerOffset: Int, val checked: Boolean) {
    val completed: Boolean get() = originalLine[markerOffset].equals('x', ignoreCase = true)
}

object NoteTasks {
    private val task = Regex("""^(\s*(?:>\s*)*(?:(?:[-+*]|\d+[.)])\s+)+\[)([^\]\r\n])(]\s+.*)$""")
    private val fence = Regex("""^ {0,3}(?:>\s*)?(`{3,}|~{3,})(.*)$""")

    fun find(markdown: String): List<NoteTask> {
        var fenceChar: Char? = null
        var fenceLength = 0
        return markdown.split('\n').mapIndexedNotNull { index, raw ->
            val line = raw.removeSuffix("\r")
            val delimiter = fence.find(line)
            if (delimiter != null) {
                val marker = delimiter.groupValues[1]
                if (fenceChar == null) {
                    fenceChar = marker[0]
                    fenceLength = marker.length
                } else if (marker[0] == fenceChar && marker.length >= fenceLength &&
                    delimiter.groupValues[2].isBlank()) {
                    fenceChar = null
                }
                return@mapIndexedNotNull null
            }
            if (fenceChar != null) return@mapIndexedNotNull null
            val match = task.matchEntire(line) ?: return@mapIndexedNotNull null
            NoteTask(index, line, match.groupValues[1].length, match.groupValues[2] != " ")
        }
    }

    fun toggle(markdown: String, line: Int, originalLine: String): String? {
        val target = find(markdown).firstOrNull { it.line == line && it.originalLine == originalLine }
            ?: return null
        val lines = markdown.split('\n').toMutableList()
        val raw = lines[line]
        lines[line] = raw.replaceRange(target.markerOffset, target.markerOffset + 1,
            if (target.checked) " " else "x")
        return lines.joinToString("\n")
    }

    fun fingerprint(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
