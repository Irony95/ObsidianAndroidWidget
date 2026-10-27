package com.example.obsidianandroidwidgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.ReplacementSpan

internal object WidgetIcons {
    fun callout(kind: String): Int = when (kind) {
        "abstract", "summary", "tldr" -> R.drawable.widget_icon_clipboard_list
        "info" -> R.drawable.widget_icon_info
        "todo" -> R.drawable.widget_icon_circle_check
        "tip", "hint", "important" -> R.drawable.widget_icon_flame
        "success", "check", "done" -> R.drawable.widget_icon_check
        "question", "help", "faq" -> R.drawable.widget_icon_circle_help
        "warning", "caution", "attention" -> R.drawable.widget_icon_triangle_alert
        "failure", "fail", "missing" -> R.drawable.widget_icon_x
        "danger", "error" -> R.drawable.widget_icon_zap
        "bug" -> R.drawable.widget_icon_bug
        "example" -> R.drawable.widget_icon_list
        "quote", "cite" -> R.drawable.widget_icon_quote
        else -> R.drawable.widget_icon_pencil
    }
}

internal class WidgetIconSpan(context: Context, resource: Int, colour: Int) : ReplacementSpan() {
    private val icon = requireNotNull(context.getDrawable(resource)).mutate().apply { setTint(colour) }
    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?) =
        (paint.textSize * 1.4f).toInt()
    override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float,
        top: Int, y: Int, bottom: Int, paint: Paint) {
        val size = paint.textSize.toInt()
        val high = (y + (paint.ascent() + paint.descent() - size) / 2f).toInt()
        icon.setBounds(x.toInt(), high, x.toInt() + size, high + size)
        icon.draw(canvas)
    }
}
