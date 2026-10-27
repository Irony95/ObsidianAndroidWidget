package com.example.obsidianandroidwidgets

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.TextPaint
import io.noties.markwon.core.spans.CodeSpan
import org.commonmark.node.Code
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.IndentedCodeBlock
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.MarkwonVisitor
import io.noties.markwon.core.CoreProps
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.core.factory.ListItemSpanFactory
import io.noties.markwon.core.spans.OrderedListItemSpan
import io.noties.markwon.ext.tasklist.TaskListItem
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.internal.ParagraphParser
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.BulletList
import org.commonmark.node.CustomNode
import org.commonmark.node.Image
import org.commonmark.node.Link
import org.commonmark.node.ListBlock
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import org.commonmark.parser.block.AbstractBlockParserFactory
import org.commonmark.parser.block.BlockStart
import org.commonmark.parser.block.MatchedBlockParser
import org.commonmark.parser.block.ParserState
import org.commonmark.parser.delimiter.DelimiterProcessor
import org.commonmark.parser.delimiter.DelimiterRun
import java.util.IdentityHashMap

class WidgetMarkdownPlugin(private val accentColor: Int, private val codeBackground: Int = 0x22888888,
    private val codeBottomPadding: Float = 0f, private val highlightTextColor: Int = 0xFF000000.toInt()) : AbstractMarkwonPlugin() {
    private val orderedMarkers = IdentityHashMap<ListItem, String>()
    private val orderedMarker = io.noties.markwon.Prop.of<String>("widget-ordered-marker")
    override fun processMarkdown(markdown: String): String = WidgetMarkdown.normalize(markdown)

    override fun configureParser(builder: Parser.Builder) {
        builder.customDelimiterProcessor(HighlightDelimiterProcessor())
            .customBlockParserFactory(LiteralNumberAfterListMarkerFactory())
    }

    override fun configureTheme(builder: MarkwonTheme.Builder) {
        builder.headingBreakHeight(0)
            .headingTextSizeMultipliers(floatArrayOf(2f, 1.6f, 1.37f, 1.25f, 1.12f, 1f))
            .blockQuoteColor(accentColor)
    }

    override fun beforeRender(node: Node) {
        node.accept(object : AbstractVisitor() {
            override fun visit(orderedList: OrderedList) {
                var number = orderedList.startNumber
                var item = orderedList.firstChild
                while (item != null) {
                    if (item is ListItem) orderedMarkers[item] = "$number${orderedList.delimiter}\u00a0"
                    if (item is ListItem || item is TaskListItem) number++
                    item = item.next
                }
                super.visit(orderedList)
            }
        })
    }

    override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
        builder.setFactory(Code::class.java) { configuration, _ ->
            val theme = configuration.theme()
            arrayOf(object : CodeSpan(theme) {
                override fun updateDrawState(paint: TextPaint) {
                    super.updateDrawState(paint)
                    paint.bgColor = android.graphics.Color.TRANSPARENT
                }
            }, WidgetHighlightSpan(codeBackground, codeBottomPadding))
        }
        val standardListItem = ListItemSpanFactory()
        builder.setFactory(ListItem::class.java) { configuration, props ->
            if (CoreProps.LIST_ITEM_TYPE.require(props) == CoreProps.ListItemType.BULLET) {
                RoundBulletSpan(configuration.theme())
            } else {
                props.get(orderedMarker)?.let { OrderedListItemSpan(configuration.theme(), it) }
                    ?: standardListItem.getSpans(configuration, props)
            }
        }
    }

    override fun configureVisitor(builder: MarkwonVisitor.Builder) {
        builder.blockHandler(CompactBlockHandler)
        builder.on(Code::class.java) { visitor, node ->
            val start = visitor.length()
            visitor.builder().append(node.literal)
            visitor.setSpansForNodeOptional(node, start)
        }
        builder.on(FencedCodeBlock::class.java) { visitor, node ->
            visitor.blockStart(node)
            val start = visitor.length()
            visitor.renderProps().set(CoreProps.CODE_BLOCK_INFO, node.info)
            visitor.builder().append(node.literal.removeSuffix("\n"))
            visitor.setSpansForNodeOptional(node, start)
            visitor.blockEnd(node)
        }
        builder.on(IndentedCodeBlock::class.java) { visitor, node ->
            visitor.blockStart(node)
            val start = visitor.length()
            visitor.renderProps().set(CoreProps.CODE_BLOCK_INFO, null)
            visitor.builder().append(node.literal.removeSuffix("\n"))
            visitor.setSpansForNodeOptional(node, start)
            visitor.blockEnd(node)
        }
        builder.on(SoftLineBreak::class.java) { visitor, _ -> visitor.forceNewLine() }
        builder.on(WidgetHighlight::class.java) { visitor, node ->
            val start = visitor.length()
            visitor.visitChildren(node)
            visitor.setSpans(start, arrayOf(WidgetHighlightSpan(node.color), ForegroundColorSpan(highlightTextColor)))
        }
        builder.on(ListItem::class.java) { visitor, item ->
            val start = visitor.length()
            visitor.visitChildren(item)
            visitor.renderProps().set(CoreProps.LIST_ITEM_TYPE, if (item.parent is OrderedList)
                CoreProps.ListItemType.ORDERED else CoreProps.ListItemType.BULLET)
            visitor.renderProps().set(orderedMarker, orderedMarkers[item])
            visitor.setSpansForNodeOptional(item, start)
            if (visitor.hasNext(item)) visitor.ensureNewLine()
        }
        val listVisitor = MarkwonVisitor.NodeVisitor<ListBlock> { visitor, node ->
            visitor.blockStart(node)
            val start = visitor.length()
            visitor.visitChildren(node)
            visitor.setSpansForNodeOptional(node, start)
            if (node.parent is ListItem || node.parent is TaskListItem) {
                visitor.setSpans(start, IndentGuideSpan(visitor.configuration().theme().blockMargin))
            }
            visitor.blockEnd(node)
        }
        builder.on(BulletList::class.java, listVisitor)
        builder.on(OrderedList::class.java, listVisitor)
    }
}

object WidgetMarkdown {
    fun normalize(markdown: String): String = markdown.replace("\r\n", "\n").replace('\r', '\n')
}

internal class WidgetHighlight(val color: Int = 0x66FFFF00) : CustomNode()

internal class HighlightDelimiterProcessor : DelimiterProcessor {
    override fun getOpeningCharacter(): Char = '='
    override fun getClosingCharacter(): Char = '='
    override fun getMinLength(): Int = 2

    override fun getDelimiterUse(opener: DelimiterRun, closer: DelimiterRun): Int =
        if (opener.length() == 2 && closer.length() == 2) 2 else 0

    override fun process(opener: Text, closer: Text, delimiterUse: Int) {
        if (generateSequence(opener.parent) { it.parent }.any { it is Link || it is Image }) {
            opener.insertAfter(Text("=="))
            closer.insertBefore(Text("=="))
            return
        }
        val first = opener.next as? Text
        val colours = mapOf("\uD83D\uDD34" to 0x4CE93147, "\uD83D\uDFE0" to 0x4CEC7500,
            "\uD83D\uDFE1" to 0x4CE0AC00, "\uD83D\uDFE2" to 0x4C08B94E,
            "\uD83D\uDD35" to 0x4C086DDD, "\uD83D\uDFE3" to 0x4C7852EE)
        val colour = colours.entries.firstOrNull { first?.literal?.startsWith(it.key) == true }
        if (colour != null) first!!.literal = first.literal.removePrefix(colour.key).removePrefix("\uFE0F")
        val highlight = WidgetHighlight(colour?.value ?: 0x66FFFF00)
        var child = opener.next
        while (child != null && child !== closer) {
            val next = child.next
            highlight.appendChild(child)
            child = next
        }
        opener.insertAfter(highlight)
    }
}

private class LiteralNumberAfterListMarkerFactory : AbstractBlockParserFactory() {
    private val number = Regex("[0-9]{1,9}[.)](?=[ \\t]|$)")
    private val markerSuffix = Regex("(?:[-+*]|[0-9]{1,9}[.)])[ \\t]+$")

    override fun tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): BlockStart? {
        val item = matchedBlockParser.matchedBlockParser.block
        if (state.indent >= 4 || item !is ListItem ||
            (item.parent !is BulletList && item.parent !is OrderedList)) {
            return BlockStart.none()
        }
        val index = state.nextNonSpaceIndex
        val line = state.line.toString()
        if (!markerSuffix.containsMatchIn(line.substring(0, index)) ||
            number.find(line, index)?.range?.first != index
        ) {
            return BlockStart.none()
        }
        return BlockStart.of(ParagraphParser()).atIndex(index)
    }
}

internal object CompactBlockHandler : MarkwonVisitor.BlockHandler {
    override fun blockStart(visitor: MarkwonVisitor, node: Node) = visitor.ensureNewLine()

    override fun blockEnd(visitor: MarkwonVisitor, node: Node) {
        if (node.next != null) {
            visitor.ensureNewLine()
            if ((node.next as? ProseIndentPlugin.IndentedProse)?.separated == true ||
                (node is Paragraph && node.next is Paragraph && node.next !is ProseIndentPlugin.IndentedProse)) {
                visitor.forceNewLine()
            }
        }
    }
}

private class RoundBulletSpan(private val theme: MarkwonTheme) : LeadingMarginSpan {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun getLeadingMargin(first: Boolean): Int = theme.blockMargin

    override fun drawLeadingMargin(
        canvas: Canvas, source: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout
    ) {
        if (!first || (text as? Spanned)?.getSpanStart(this) != start) return
        paint.set(source)
        theme.applyListItemStyle(paint)
        paint.style = Paint.Style.FILL
        paint.isAntiAlias = true
        val centerX = x + dir * theme.blockMargin / 2f
        val centerY = baseline + (source.ascent() + source.descent()) / 2f
        canvas.drawCircle(centerX, centerY, (theme.blockMargin / 12f).coerceAtLeast(1f), paint)
    }
}

private class IndentGuideSpan(private val margin: Int) : LeadingMarginSpan {
    private val paint = Paint()

    override fun getLeadingMargin(first: Boolean): Int = 0

    override fun drawLeadingMargin(
        canvas: Canvas, source: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout
    ) {
        paint.set(source)
        paint.alpha = source.alpha / 4
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (margin / 24f).coerceAtLeast(1f)
        val guideX = x - dir * margin / 2f
        canvas.drawLine(guideX, top.toFloat(), guideX, bottom.toFloat(), paint)
    }
}
