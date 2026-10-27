package com.example.obsidianandroidwidgets

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.Spanned
import android.text.style.*
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonVisitor
import io.noties.markwon.core.spans.TextViewSpan
import org.commonmark.node.*
import org.json.JSONObject

internal class CalloutNode(val kind: String, val id: String, val color: Int, val folded: Boolean, val foldable: Boolean) : CustomBlock()

internal class CalloutPlugin(private val context: android.content.Context, private val density: Float,
    private val snapshot: VaultSnapshot, private val controls: Boolean) : AbstractMarkwonPlugin() {
    override fun beforeRender(node: Node) {
        val overrides = runCatching { JSONObject(snapshot.data.optString("callouts", "{}")) }.getOrDefault(JSONObject())
        var index = 0
        fun walk(parent: Node) {
            var child = parent.firstChild
            while (child != null) {
                val next = child.next
                val paragraph = (child as? BlockQuote)?.firstChild as? Paragraph
                val text = paragraph?.firstChild as? Text
                val match = text?.literal?.let { Regex("^\\[!([\\w-]+)]([+-]?)[ \\t]*(.*)$").matchEntire(it) }
                if (match != null) {
                    val kind = match.groupValues[1].lowercase()
                    val id = NoteTasks.fingerprint("${index++}|$kind|${match.groupValues[3]}").take(16)
                    val folded = overrides.optBoolean(id, match.groupValues[2] == "-")
                    val box = CalloutNode(kind, id, color(kind), folded, match.groupValues[2].isNotEmpty())
                    text.literal = match.groupValues[3]
                    val title = Paragraph()
                    while (paragraph.firstChild != null && paragraph.firstChild !is SoftLineBreak) title.appendChild(paragraph.firstChild)
                    paragraph.firstChild?.unlink()
                    if (title.firstChild == null || (title.firstChild as? Text)?.literal?.isEmpty() == true && title.firstChild.next == null) {
                        title.appendChild(Text(kind.replaceFirstChar { it.uppercase() }))
                    }
                    if (paragraph.firstChild == null) paragraph.unlink()
                    box.appendChild(title)
                    while (child.firstChild != null) box.appendChild(child.firstChild)
                    child.insertBefore(box)
                    child.unlink()
                    walk(box)
                    if (folded) while (box.lastChild !== title) box.lastChild.unlink()
                } else walk(child)
                child = next
            }
        }
        walk(node)
    }
    override fun configureVisitor(builder: MarkwonVisitor.Builder) {
        builder.on(CalloutNode::class.java) { visitor, node ->
            visitor.blockStart(node)
            val start = visitor.length()
            val title = node.firstChild
            visitor.builder().append('\uFFFC')
            visitor.setSpans(start, WidgetIconSpan(context, WidgetIcons.callout(node.kind), node.color))
            if (node.foldable) visitor.builder().append(if (node.folded) "\u25B8 " else "\u25BE ")
            visitor.visitChildren(title)
            visitor.setSpans(start, arrayOf(StyleSpan(android.graphics.Typeface.BOLD), ForegroundColorSpan(node.color)))
            if (node.foldable && controls) visitor.setSpans(start, object : URLSpan("widget-callout://toggle?id=${node.id}&closed=${!node.folded}") {
                override fun updateDrawState(ds: android.text.TextPaint) { ds.color = node.color; ds.isUnderlineText = false }
            })
            var body = title.next
            while (body != null) { visitor.ensureNewLine(); body.accept(visitor); body = body.next }
            val inset = (12 * density).toInt()
            val outerInset = generateSequence(node.parent) { it.parent }.sumOf {
                when (it) {
                    is CalloutNode -> inset
                    is BlockQuote, is ListItem, is io.noties.markwon.ext.tasklist.TaskListItem -> visitor.configuration().theme().blockMargin
                    else -> 0
                }
            }
            visitor.setSpans(start, CalloutBoxSpan(node.color, inset, outerInset))
            visitor.blockEnd(node)
        }
    }
    private fun color(kind: String): Int = when (kind) {
        "abstract", "summary", "tldr", "tip", "hint", "important" -> 0xFF00BFA5.toInt()
        "success", "check", "done" -> 0xFF08B94E.toInt()
        "question", "help", "faq", "warning", "caution", "attention" -> 0xFFE5A300.toInt()
        "failure", "fail", "missing", "danger", "error", "bug" -> 0xFFE64F60.toInt()
        "example" -> 0xFF9B75E8.toInt()
        "quote", "cite" -> 0xFF9E9E9E.toInt()
        else -> 0xFF448AFF.toInt()
    }
}

private class CalloutBoxSpan(private val color: Int, private val inset: Int, private val outerInset: Int) : LeadingMarginSpan, LineBackgroundSpan {
    override fun getLeadingMargin(first: Boolean) = inset
    override fun drawLeadingMargin(c: Canvas, p: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout) {}
    override fun drawBackground(c: Canvas, p: Paint, left: Int, right: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, lineNumber: Int) {
        val x = left + outerInset
        val paint = Paint().apply { color = this@CalloutBoxSpan.color; alpha = 24 }
        c.drawRect(x.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), paint)
        paint.alpha = 255
        c.drawRect(x.toFloat(), top.toFloat(), (x + inset / 5f), bottom.toFloat(), paint)
    }
}
