package com.example.obsidianandroidwidgets

import android.text.style.LeadingMarginSpan
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonSpansFactory
import org.commonmark.node.Paragraph
import org.commonmark.parser.InlineParser
import org.commonmark.parser.Parser
import org.commonmark.parser.block.*

internal class ProseIndentPlugin(private val indentPx: Int) : AbstractMarkwonPlugin() {
    private val marker = "\uE120${java.util.UUID.randomUUID()}\uE121"
    private val prefix = Regex("^${Regex.escape(marker)}([0-9]+):([01]):")
    private val markdownBlock = Regex("^(?:[-+*]\\s|[0-9]+[.)]\\s|#{1,6}\\s|>|<|[-*_]{3,}\\s*$|={3,}\\s*$)")

    override fun processMarkdown(markdown: String): String {
        var fence = ""
        var math = false
        var blank = false
        var afterList = false
        var afterQuote = false
        var markedParagraph = false
        var indentedCode = false
        return markdown.split('\n').joinToString("\n") { line ->
            val plain = line.trimStart()
            val delimiter = Regex("^(`{3,}|~{3,})").find(plain)?.value
            val columns = line.takeWhile { it == ' ' || it == '\t' }.fold(0) { count, char -> count + if (char == '\t') 4 else 1 }
            val list = Regex("^(?:[-+*]|[0-9]+[.)])\\s").containsMatchIn(plain)
            if (plain.isNotBlank() && columns < 4) indentedCode = false
            if (columns >= 4 && blank && !afterList && fence.isEmpty() && !math) indentedCode = true
            val reference = Regex("^ {0,3}\\[[^]\\n]+]:").containsMatchIn(line)
            if (fence.isEmpty() && plain == "$$") math = !math
            val result = if (indentedCode) line else if (delimiter != null) {
                if (fence.isEmpty()) fence = delimiter
                else if (fence[0] == delimiter[0] && delimiter.length >= fence.length && plain.removePrefix(delimiter).isBlank()) fence = ""
                line
            } else if (fence.isNotEmpty() || math || reference || plain == "$$" || plain.isBlank() || markdownBlock.containsMatchIn(plain) || '|' in line) line
            else if (columns < 4 && !(columns == 0 && (afterList || afterQuote)) && !markedParagraph) line
            else {
                "$marker${columns / 4}:${if (blank) 1 else 0}:$plain"
            }
            markedParagraph = result.startsWith(marker)
            if (!indentedCode && list) afterList = true else if (plain.isNotBlank() && columns == 0) afterList = false
            afterQuote = fence.isEmpty() && plain.startsWith('>')
            blank = plain.isBlank()
            result
        }
    }

    override fun configureParser(builder: Parser.Builder) {
        builder.customBlockParserFactory(object : AbstractBlockParserFactory() {
            override fun tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): BlockStart? {
                val match = prefix.find(state.line.toString()) ?: return BlockStart.none()
                return BlockStart.of(ProseParser(match.groupValues[1].toInt(), match.groupValues[2] == "1")).atIndex(match.value.length)
            }
        })
    }

    override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
        builder.setFactory(IndentedProse::class.java) { _, props ->
            LeadingMarginSpan.Standard(props.get(INDENT) ?: 0)
        }
    }

    override fun configureVisitor(builder: io.noties.markwon.MarkwonVisitor.Builder) {
        builder.on(IndentedProse::class.java) { visitor, node ->
            visitor.blockStart(node)
            val start = visitor.length()
            visitor.visitChildren(node)
            visitor.renderProps().set(INDENT, node.level * indentPx)
            visitor.setSpansForNodeOptional(node, start)
            visitor.blockEnd(node)
        }
    }

    private inner class ProseParser(level: Int, separated: Boolean) : AbstractBlockParser() {
        private val paragraph = IndentedProse(level, separated)
        private val lines = mutableListOf<String>()
        override fun getBlock() = paragraph
        override fun tryContinue(state: ParserState): BlockContinue? {
            val match = prefix.find(state.line.toString()) ?: return BlockContinue.none()
            return if (match.groupValues[1].toInt() == paragraph.level) BlockContinue.atIndex(match.value.length) else BlockContinue.none()
        }
        override fun addLine(line: CharSequence) { lines += line.toString() }
        override fun parseInlines(inlineParser: InlineParser) { inlineParser.parse(lines.joinToString("\n"), paragraph) }
    }
    internal class IndentedProse(val level: Int, val separated: Boolean) : Paragraph()
    companion object {
        private val INDENT = io.noties.markwon.Prop.of<Int>("widget-prose-indent")
    }
}
