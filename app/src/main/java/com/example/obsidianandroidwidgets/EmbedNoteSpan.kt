package com.example.obsidianandroidwidgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.ReplacementSpan

internal class EmbedNoteSpan(context: Context, val url: String, val width: Int,
    val body: RenderedNote, val title: String?, private val colour: Int, textColour: Int) : ReplacementSpan() {
    private val density = context.resources.displayMetrics.density
    private val inset = (10 * density).toInt()
    private val iconSize = (20 * density).toInt()
    private val header = if (title != null || body.textLayout.getLineRight(0) > body.width - iconSize)
        (24 * density).toInt() else 0
    val height = body.height + inset * 2 + header
    val linkBounds = Rect(width - inset - iconSize, inset, width - inset, inset + iconSize)
    private val icon = requireNotNull(context.getDrawable(R.drawable.widget_icon_external_link)).mutate().apply { setTint(colour) }
    private val caption = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColour; textSize = 16 * context.resources.displayMetrics.scaledDensity; typeface = Typeface.DEFAULT_BOLD
    }
    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        fm?.apply { ascent = -height; top = ascent; descent = 0; bottom = 0 }
        return width
    }
    override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float,
        top: Int, y: Int, bottom: Int, paint: Paint) {
        val save = canvas.save()
        canvas.translate(x, (y - height).toFloat())
        val line = Paint().apply { color = colour }
        canvas.drawRect(0f, 0f, 2 * density, height.toFloat(), line)
        title?.let {
            val label = TextUtils.ellipsize(it, caption, (width - inset * 3 - iconSize).coerceAtLeast(1).toFloat(), TextUtils.TruncateAt.END)
            canvas.drawText(label, 0, label.length, inset.toFloat(), inset - caption.ascent(), caption)
        }
        body.drawInto(canvas, inset.toFloat(), (inset + header).toFloat())
        icon.bounds = linkBounds
        icon.draw(canvas)
        canvas.restoreToCount(save)
    }
}
