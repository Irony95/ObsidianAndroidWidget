package com.example.obsidianandroidwidgets

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.style.ReplacementSpan
import android.text.style.LineHeightSpan
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonVisitor
import io.noties.markwon.core.CoreProps
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Node

internal class CodeBlockPlugin(private val width: Int, private val density: Float, private val scaledDensity: Float,
    private val controls: Boolean) : AbstractMarkwonPlugin() {
    override fun configureVisitor(builder: MarkwonVisitor.Builder) {
        fun render(visitor: MarkwonVisitor, node: Node, language: String, source: String) {
            visitor.blockStart(node)
            val start = visitor.length()
            visitor.renderProps().set(CoreProps.CODE_BLOCK_INFO, language)
            if (controls) {
                val theme = visitor.configuration().theme()
                val margin = theme.codeBlockMargin
                visitor.builder().append('\uFFFC')
                visitor.setSpans(start, CodeCopySpan(source, language.substringBefore(' '),
                    (markdownContentWidth(node, width, theme.blockMargin, (12 * density).toInt(), (4 * density).toInt()) - margin * 2).coerceAtLeast(1), density, scaledDensity))
                visitor.forceNewLine()
            } else {
                val pad = visitor.length()
                visitor.builder().append('\uFFFC')
                visitor.setSpans(pad, CodePaddingSpan((8 * density).toInt()))
                visitor.forceNewLine()
            }
            visitor.builder().append(visitor.configuration().syntaxHighlight().highlight(language.substringBefore(' ').lowercase(), source.removeSuffix("\n")))
            visitor.forceNewLine()
            val pad = visitor.length()
            visitor.builder().append('\uFFFC')
            visitor.setSpans(pad, CodePaddingSpan((6 * density).toInt()))
            visitor.setSpansForNodeOptional(node, start)
            visitor.blockEnd(node)
        }
        builder.on(FencedCodeBlock::class.java) { visitor, node -> render(visitor, node, node.info.orEmpty(), node.literal) }
        builder.on(IndentedCodeBlock::class.java) { visitor, node -> render(visitor, node, "", node.literal) }
    }
}

internal fun markdownContentWidth(node: Node, width: Int, margin: Int, calloutInset: Int, tablePadding: Int): Int {
    var result = width
    generateSequence(node.parent) { it.parent }.forEach { parent ->
        result -= when (parent) {
            is CalloutNode -> calloutInset
            is org.commonmark.node.ListItem, is io.noties.markwon.ext.tasklist.TaskListItem,
            is org.commonmark.node.BlockQuote -> margin
            else -> 0
        }
        if (parent is org.commonmark.ext.gfm.tables.TableCell) {
            var count = 0
            var cell = parent.parent.firstChild
            while (cell != null) { count++; cell = cell.next }
            result = result / count.coerceAtLeast(1) - tablePadding * 2
        }
    }
    return result.coerceAtLeast(1)
}

internal class CodeCopySpan(val code: String, val language: String, private val width: Int,
    private val density: Float, private val scaledDensity: Float) : ReplacementSpan(), LineHeightSpan {
    val controlWidth: Int get() = if (language.isBlank()) (32 * density).toInt() else
        (caption.measureText(language) + 16 * density).toInt().coerceAtMost(width)
    private val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12 * scaledDensity; typeface = Typeface.DEFAULT }
    override fun chooseHeight(text: CharSequence, start: Int, end: Int, spanstartv: Int, v: Int, fm: Paint.FontMetricsInt) {
        fm.ascent = -(28 * density).toInt(); fm.top = fm.ascent; fm.descent = 0; fm.bottom = 0
    }
    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        fm?.apply { ascent = -(28 * density).toInt(); top = ascent; descent = 0; bottom = 0 }
        return width
    }
    override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        caption.color = paint.color
        if (language.isNotBlank()) {
            caption.style = Paint.Style.FILL
            canvas.drawText(language, x + width - caption.measureText(language) - 6 * density, y - 8 * density, caption)
        } else {
            caption.style = Paint.Style.STROKE
            caption.strokeWidth = 1.5f * density
            val left = x + width - 23 * density
            val high = y - 22 * density
            canvas.drawRoundRect(left, high, left + 13 * density, high + 13 * density, density, density, caption)
            canvas.drawRoundRect(left + 4 * density, high + 4 * density, left + 17 * density, high + 17 * density, density, density, caption)
        }
    }
}

private class CodePaddingSpan(private val height: Int) : ReplacementSpan(), LineHeightSpan {
    override fun chooseHeight(text: CharSequence, start: Int, end: Int, spanstartv: Int, v: Int, fm: Paint.FontMetricsInt) {
        fm.ascent = -height; fm.top = fm.ascent; fm.descent = 0; fm.bottom = 0
    }
    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        fm?.apply { ascent = -height; top = ascent; descent = 0; bottom = 0 }
        return 1
    }
    override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {}
}
