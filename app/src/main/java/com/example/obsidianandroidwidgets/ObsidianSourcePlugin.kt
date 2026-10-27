package com.example.obsidianandroidwidgets

import android.text.style.RelativeSizeSpan
import android.text.style.SuperscriptSpan
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonVisitor
import org.commonmark.node.*
import org.commonmark.parser.Parser

internal class ObsidianSourcePlugin : AbstractMarkwonPlugin() {
    private val definitions = linkedMapOf<String, String>()
    private val numbers = linkedMapOf<String, Int>()
    private val references = Regex("\\[\\^([^]]+)]|\\^\\[([^]]+)]")
    companion object {
        fun withoutFrontmatter(markdown: String): String {
            val lines = markdown.split('\n').toMutableList()
            if (lines.firstOrNull()?.trim() == "---") {
                val end = (1 until lines.size).firstOrNull { lines[it].trim() in listOf("---", "...") }
                if (end != null) for (i in 0..end) lines[i] = ""
            }
            return lines.joinToString("\n")
        }
    }
    override fun processMarkdown(markdown: String): String {
        val lines = withoutFrontmatter(markdown).split('\n').toMutableList()
        var fence = ""
        var comment = false
        var definition: String? = null
        for (i in lines.indices) {
            val line = lines[i]
            val delimiter = Regex("^\\s*(?:>\\s*)*(`{3,}|~{3,})(.*)$").find(line)
            if (!comment && delimiter != null) {
                val token = delimiter.groupValues[1]
                if (fence.isEmpty()) fence = token
                else if (fence[0] == token[0] && token.length >= fence.length && delimiter.groupValues[2].isBlank()) fence = ""
                definition = null
                continue
            }
            if (fence.isNotEmpty()) continue
            val clean = StringBuilder()
            var ticks = 0
            var index = 0
            while (index < line.length) {
                if (!comment && line[index] == '`') {
                    var end = index
                    while (end < line.length && line[end] == '`') end++
                    val count = end - index
                    if (ticks == 0) ticks = count else if (ticks == count) ticks = 0
                    clean.append(line.substring(index, end)); index = end
                } else if (ticks == 0 && line.startsWith("%%", index) && (index == 0 || line[index - 1] != '\\')) {
                    comment = !comment; index += 2
                } else {
                    if (!comment) clean.append(line[index])
                    index++
                }
            }
            var value = clean.toString()
            val footnote = Regex("^ {0,3}\\[\\^([^]]+)]:\\s*(.*)$").matchEntire(value)
            if (footnote != null) {
                definition = footnote.groupValues[1]
                definitions[definition] = footnote.groupValues[2]
                value = ""
            } else if (definition != null && (value.startsWith("    ") || value.startsWith('\t'))) {
                definitions[definition] = definitions[definition] + "\n" + value.trimStart()
                value = ""
            } else if (value.isNotBlank()) definition = null
            if ('`' !in value) value = value.replace(Regex("(?<!\\\\)(?:^|\\s+)\\^[A-Za-z0-9-]+\\s*$"), "")
            val inlineCode = Regex("(`+).*?\\1").findAll(value).map { it.range }.toList()
            value = Regex("<iframe\\b[^>]*\\bsrc=[\"']([^\"']+)[\"'][^>]*>.*?</iframe>", RegexOption.IGNORE_CASE)
                .replace(value) { match -> if (inlineCode.any { match.range.first in it }) match.value
                    else "[${match.groupValues[1]}](${match.groupValues[1]})" }
            lines[i] = value
        }
        return lines.joinToString("\n")
    }

    override fun beforeRender(node: Node) {
        val texts = mutableListOf<Text>()
        node.accept(object : AbstractVisitor() {
            override fun visit(text: Text) {
                if (generateSequence(text.parent) { it.parent }.none { it is Link || it is Image }) texts += text
            }
        })
        texts.forEach { text ->
            var offset = 0
            references.findAll(text.literal).forEach { match ->
                val id = if (match.groupValues[1].isNotEmpty()) match.groupValues[1] else "inline:${numbers.size}"
                if (match.groupValues[2].isNotEmpty()) definitions[id] = match.groupValues[2]
                if (!definitions.containsKey(id)) return@forEach
                val number = numbers.getOrPut(id) { numbers.size + 1 }
                text.insertBefore(Text(text.literal.substring(offset, match.range.first)))
                text.insertBefore(Footnote(number))
                offset = match.range.last + 1
            }
            if (offset > 0) {
                text.insertBefore(Text(text.literal.substring(offset)))
                text.unlink()
            }
        }
        if (numbers.isNotEmpty()) {
            node.appendChild(Heading().apply { level = 6; appendChild(Text("Footnotes")) })
            numbers.forEach { (id, number) ->
                val parsed = Parser.builder().build().parse("$number. ${definitions[id]}")
                var child = parsed.firstChild
                while (child != null) { val next = child.next; node.appendChild(child); child = next }
            }
        }
    }
    override fun configureVisitor(builder: MarkwonVisitor.Builder) {
        builder.on(Footnote::class.java) { visitor, node ->
            val start = visitor.length()
            visitor.builder().append("[${node.number}]")
            visitor.setSpans(start, arrayOf(RelativeSizeSpan(0.75f), SuperscriptSpan()))
        }
    }
    private class Footnote(val number: Int) : CustomNode()
}
