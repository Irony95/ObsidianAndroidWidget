package com.example.obsidianandroidwidgets

import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonVisitor
import io.noties.markwon.ext.tasklist.TaskListItem
import org.commonmark.node.*
import java.util.UUID

internal class ListSpacingPlugin : AbstractMarkwonPlugin() {
    private val prefix = "\uE004" + UUID.randomUUID() + ":"
    private val token = Regex(Regex.escape(prefix) + "(\\d+):(\\d+)\uE005(?: |$)")
    private val quotePrefix = Regex("^(?:[ \\t]*>[ \\t]?)*")
    private val itemPrefix = Regex("^(?:[ \\t]*(?:[-+*]|\\d{1,9}[.)])\\s+)+(?:\\[[^\\]\\r\\n]\\]\\s+)?")
    private val heading = Regex("^ {0,3}#{1,6}[ \\t]+")
    private val rule = Regex("^(?:(?:-[ \\t]*){3,}|(?:\\*[ \\t]*){3,}|(?:_[ \\t]*){3,}|=+)$")

    override fun processMarkdown(markdown: String): String {
        var blanks = 0
        var blankDepth = 0
        var fence = ""
        var fenceQuotes = 0
        var math = false
        return markdown.split('\n').joinToString("\n") { line ->
            val quote = quotePrefix.find(line)!!.value
            val content = line.substring(quote.length)
            val depth = quote.count { it == '>' }
            val itemStart = itemPrefix.find(content)?.value.orEmpty()
            var fenceLine = if (fence.isEmpty()) content.substring(itemStart.length) else line
            if (fence.isNotEmpty()) repeat(fenceQuotes) {
                fenceLine = fenceLine.replaceFirst(Regex("^[ \\t]*>[ \\t]?"), "")
            }
            val delimiter = Regex("^[ \\t]*(`{3,}|~{3,})(.*)$").find(fenceLine)
            if (fence.isNotEmpty()) {
                if (delimiter != null && delimiter.groupValues[1][0] == fence[0] &&
                    delimiter.groupValues[1].length >= fence.length && delimiter.groupValues[2].isBlank()) fence = ""
                line
            } else if (math) {
                if (content.trim() == "$$") math = false
                line
            } else if (content.isBlank()) {
                blankDepth = if (blanks == 0) depth else minOf(blankDepth, depth)
                blanks++
                line
            } else {
                val marker = if (blanks > 0) "$prefix$blanks:$blankDepth\uE005 " else ""
                blanks = 0
                when {
                    delimiter != null -> {
                        fence = delimiter.groupValues[1]
                        fenceQuotes = depth
                        if (marker.isEmpty()) line else "$line $marker"
                    }
                    content.trim() == "$$" -> { math = true; line }
                    marker.isEmpty() || rule.matches(content.trim()) || content.trimStart().startsWith('<') ||
                        Regex("^ {0,3}\\[[^]\\n]+]:").containsMatchIn(content) -> line
                    else -> {
                        val item = itemPrefix.find(content)?.value
                            ?: Regex("^[ \\t]+").find(content)?.value.orEmpty()
                        val rest = content.substring(item.length)
                        val innerQuote = quotePrefix.find(rest)!!.value
                        val inner = rest.substring(innerQuote.length)
                        val structural = innerQuote + (heading.find(inner)?.value ?: Regex("^ {0,3}\\|[ \\t]*").find(inner)?.value.orEmpty())
                        quote + item + structural + marker + rest.substring(structural.length)
                    }
                }
            }
        }
    }

    override fun beforeRender(node: Node) {
        fun clean(value: String) = token.replace(value, "")
        fun gapBefore(current: Node, match: MatchResult) {
            var block = generateSequence(current) { it.parent }.firstOrNull { it is Block } ?: return
            val item = generateSequence(current.parent) { it.parent }.firstOrNull { it is ListItem || it is TaskListItem }
            if (item != null && item.firstChild === block) block = if (item.previous != null) item else item.parent
            if (match.groupValues[2] == "0") {
                while (block.parent is BlockQuote && block.parent.firstChild === block) block = block.parent
            }
            if (block.previous !is SourceGap) block.insertBefore(SourceGap(match.groupValues[1].toInt()))
        }
        fun visit(current: Node) {
            if (current is Text) token.find(current.literal)?.let { gapBefore(current, it) }
            if (current is FencedCodeBlock) {
                token.find(current.info.orEmpty())?.let { gapBefore(current, it) }
                current.info = clean(current.info.orEmpty()).trimEnd()
            }
            if (current is IndentedCodeBlock) {
                token.find(current.literal)?.let { gapBefore(current, it) }
                current.literal = clean(current.literal)
            }
            when (current) {
                is Text -> current.literal = clean(current.literal)
                is Code -> current.literal = clean(current.literal)
            }
            generateSequence(current.firstChild) { it.next }.toList().forEach(::visit)
        }
        visit(node)
    }

    override fun configureVisitor(builder: MarkwonVisitor.Builder) {
        builder.on(SourceGap::class.java) { visitor, gap ->
            if (visitor.length() > 0) {
                visitor.ensureNewLine()
                repeat(gap.lines) { visitor.forceNewLine() }
            }
        }
    }
}

private class SourceGap(val lines: Int) : CustomBlock()
