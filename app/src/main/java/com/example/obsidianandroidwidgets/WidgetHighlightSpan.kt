package com.example.obsidianandroidwidgets

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.text.Spanned
import android.text.style.LineBackgroundSpan
import io.noties.markwon.core.spans.TextViewSpan
import io.noties.markwon.core.spans.TextLayoutSpan
import kotlin.math.max
import kotlin.math.min

internal class WidgetHighlightSpan(val backgroundColor: Int, private val bottomPadding: Float = 0f) : LineBackgroundSpan {
    private val fill = Paint()
    private val path = Path()

    override fun drawBackground(canvas: Canvas, paint: Paint, left: Int, right: Int,
        top: Int, baseline: Int, bottom: Int, text: CharSequence, start: Int, end: Int, lineNumber: Int) {
        val spans = text as? Spanned ?: return
        val from = max(start, spans.getSpanStart(this))
        val layout = TextLayoutSpan.layoutOf(text) ?: TextViewSpan.textViewOf(text)?.layout
        val to = min(end, spans.getSpanEnd(this)).let {
            if (layout != null) min(it, layout.getLineVisibleEnd(lineNumber)) else it
        }
        if (to <= from) return
        fill.set(paint)
        fill.color = backgroundColor
        fill.style = Paint.Style.FILL
        val ascent = layout?.getLineAscent(lineNumber)?.toFloat() ?: paint.ascent()
        val scale = if (paint.ascent() < 0) ascent / paint.ascent() else 1f
        val fontTop = baseline + ascent
        val fontBottom = min(bottom.toFloat(), baseline + paint.descent() * scale + bottomPadding)
        if (layout == null) {
            val x = left + paint.measureText(text, start, from)
            canvas.drawRect(x, fontTop, x + paint.measureText(text, from, to), fontBottom, fill)
        } else {
            path.reset()
            layout.getSelectionPath(from, to, path)
            canvas.save()
            canvas.clipRect(left.toFloat(), fontTop, right.toFloat(), fontBottom)
            canvas.drawPath(path, fill)
            canvas.restore()
        }
    }
}
