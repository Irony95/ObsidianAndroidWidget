package com.example.obsidianandroidwidgets

import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.ReplacementSpan
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonVisitor
import org.commonmark.node.*
import org.commonmark.parser.Parser
import org.commonmark.parser.block.*
import org.commonmark.parser.delimiter.*
import ru.noties.jlatexmath.JLatexMathDrawable

internal class MathPlugin(context: android.content.Context, private val width: Int, private val size: Float, private val color: Int) : AbstractMarkwonPlugin() {
    init { ru.noties.jlatexmath.JLatexMathAndroid.init(context.applicationContext) }
    override fun configureParser(builder: Parser.Builder) {
        builder.customDelimiterProcessor(object : DelimiterProcessor {
            override fun getOpeningCharacter() = '$'
            override fun getClosingCharacter() = '$'
            override fun getMinLength() = 1
            override fun getDelimiterUse(opener: DelimiterRun, closer: DelimiterRun) =
                if (opener.length() == closer.length() && opener.length() <= 2) opener.length() else 0
            override fun process(opener: Text, closer: Text, delimiterUse: Int) {
                val math = InlineMath()
                var child = opener.next
                while (child != null && child !== closer) { val next = child.next; math.appendChild(child); child = next }
                opener.insertAfter(math)
            }
        })
        builder.customBlockParserFactory(object : AbstractBlockParserFactory() {
            override fun tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): BlockStart? {
                val line = state.line.toString().substring(state.nextNonSpaceIndex)
                if (!line.startsWith("$$") || state.indent > 3) return BlockStart.none()
                return BlockStart.of(MathParser(line.removePrefix("$$"))).atIndex(state.line.length)
            }
        })
    }
    override fun configureVisitor(builder: MarkwonVisitor.Builder) {
        builder.on(InlineMath::class.java) { visitor, node ->
            val formula = StringBuilder()
            node.accept(object : AbstractVisitor() {
                override fun visit(text: Text) { formula.append(text.literal) }
                override fun visit(softLineBreak: SoftLineBreak) { formula.append(' ') }
            })
            append(visitor, formula.toString(), false)
        }
        builder.on(BlockMath::class.java) { visitor, node ->
            visitor.blockStart(node)
            append(visitor, node.lines.joinToString("\n"), true)
            visitor.blockEnd(node)
        }
    }
    private fun append(visitor: MarkwonVisitor, source: String, block: Boolean) {
        val drawable = runCatching { JLatexMathDrawable.builder(source).textSize(size).color(color).fitCanvas(false).build() }.getOrNull()
        if (drawable == null) { visitor.builder().append(source); return }
        val scale = minOf(1f, width.toFloat() / drawable.intrinsicWidth.coerceAtLeast(1))
        val high = (drawable.intrinsicHeight * scale).toInt().coerceAtLeast(1)
        val spanWidth = if (block) width else (drawable.intrinsicWidth * scale).toInt().coerceAtLeast(1)
        val depth = if (block) 0 else (drawable.icon().iconDepth * scale).toInt()
        drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
        val start = visitor.length()
        visitor.builder().append('\uFFFC')
        visitor.setSpans(start, object : ReplacementSpan() {
            override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
                fm?.apply { ascent = -high + depth; top = ascent; descent = depth; bottom = depth }
                return spanWidth
            }
            override fun draw(c: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, p: Paint) {
                c.save()
                c.translate(x + if (block) (width - drawable.intrinsicWidth * scale) / 2 else 0f, (y - high + depth).toFloat())
                c.scale(scale, scale)
                drawable.draw(c)
                c.restore()
            }
        })
    }
    private class InlineMath : CustomNode()
    private class BlockMath : CustomBlock() { val lines = mutableListOf<String>() }
    private class MathParser(initial: String) : AbstractBlockParser() {
        private val node = BlockMath()
        private var first = true
        private val complete = initial.trimEnd().endsWith("$$")
        init { if (initial.isNotBlank()) node.lines += if (complete) initial.trimEnd().removeSuffix("$$") else initial }
        override fun getBlock() = node
        override fun tryContinue(state: ParserState): BlockContinue? = when {
            complete -> BlockContinue.none()
            state.line.toString().trim() == "$$" -> BlockContinue.finished()
            else -> BlockContinue.atIndex(state.index)
        }
        override fun addLine(line: CharSequence) { if (first) first = false else node.lines += line.toString() }
    }
}
