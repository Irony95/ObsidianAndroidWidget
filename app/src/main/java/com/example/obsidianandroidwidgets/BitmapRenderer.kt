package com.example.obsidianandroidwidgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.text.style.URLSpan
import android.text.style.LineHeightSpan
import android.text.style.ReplacementSpan
import android.graphics.Paint
import android.graphics.Typeface
import android.text.util.Linkify
import androidx.core.text.util.LinkifyCompat
import android.view.View
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tables.TableTheme
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.ext.tasklist.TaskListSpan
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

class BitmapRenderer(private val context: Context) {
    companion object {
        const val MAX_TILE_BYTES = 256 * 1024
    }

    fun layoutNote(
        markdown: String,
        width: Int,
        backgroundColor: Int,
        textColor: Int,
        accentColor: Int = textColor,
        tileByteLimit: Int = MAX_TILE_BYTES,
        tileHeightDp: Int = 128,
        vaultSnapshot: VaultSnapshot = VaultSnapshot(),
        embedDepth: Int = 0,
        interactive: Boolean = true,
    ): RenderedNote {
        val renderWidth = width.coerceAtLeast(1)
        val density = context.resources.displayMetrics.density
        val markwon = Markwon.builder(context)
            .fallbackToRawInputWhenEmpty(false)
            .usePlugin(ObsidianSourcePlugin())
            .usePlugin(io.noties.markwon.syntax.SyntaxHighlightPlugin.create(WidgetGrammarBundle.prism(),
                AtomCodeTheme(androidx.core.graphics.ColorUtils.calculateLuminance(backgroundColor) < 0.5)))
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder.headingBreakHeight(0)
                        .headingTextSizeMultipliers(floatArrayOf(2f, 1.6f, 1.37f, 1.25f, 1.12f, 1f))
                        .blockMargin((24 * density).toInt())
                        .blockQuoteColor(accentColor)
                        .linkColor(accentColor)
                        .codeTypeface(Typeface.MONOSPACE)
                        .codeBlockTypeface(Typeface.MONOSPACE)
                        .codeTextSize((14 * context.resources.displayMetrics.scaledDensity).toInt())
                        .codeBlockTextSize((14 * context.resources.displayMetrics.scaledDensity).toInt())
                        .codeBackgroundColor(androidx.core.graphics.ColorUtils.setAlphaComponent(textColor, 32))
                        .codeBlockBackgroundColor(androidx.core.graphics.ColorUtils.compositeColors(
                            androidx.core.graphics.ColorUtils.setAlphaComponent(textColor, 24), backgroundColor))
                }
            })
            .usePlugin(TaskListPlugin.create(textColor, textColor, backgroundColor))
            .usePlugin(TablePlugin.create(TableTheme.buildWithDefaults(context)
                .tableCellPadding((4 * density).toInt())
                .tableBorderColor(android.graphics.Color.TRANSPARENT).build()))
            .usePlugin(ImagesPlugin.create())
            .usePlugin(SoftBreakAddsNewLinePlugin.create())
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(HtmlPlugin.create())
            .usePlugin(WidgetMarkdownPlugin(accentColor, androidx.core.graphics.ColorUtils.setAlphaComponent(textColor, 32), 2 * density, textColor))
            .usePlugin(CodeBlockPlugin(renderWidth, density, context.resources.displayMetrics.scaledDensity, embedDepth == 0))
            .usePlugin(ListSpacingPlugin())
            .usePlugin(ProseIndentPlugin((16 * density).toInt()))
            .usePlugin(MathPlugin(context, renderWidth, 16 * context.resources.displayMetrics.scaledDensity, textColor))
            .usePlugin(VaultSyntaxPlugin(context, vaultSnapshot, renderWidth, backgroundColor, textColor, accentColor, embedDepth))
            .usePlugin(TaskSourcePlugin(markdown, textColor, backgroundColor, interactive))
            .usePlugin(CalloutPlugin(context, density, vaultSnapshot, embedDepth == 0))
            .build()
        val textView = TextView(context).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(renderWidth, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            setTextColor(textColor)
            setLinkTextColor(accentColor)
            setBackgroundColor(backgroundColor)
            textSize = 16f
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
            setLineSpacing(0f, 1f)
        }
        markwon.setMarkdown(textView, markdown)
        val text = SpannableStringBuilder(textView.text)
        text.setSpan(TextLineSpacing(), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val detected = SpannableStringBuilder(text.toString())
        LinkifyCompat.addLinks(detected, Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES)
        detected.getSpans(0, detected.length, URLSpan::class.java).forEach { link ->
            val start = detected.getSpanStart(link)
            val end = detected.getSpanEnd(link)
            if (text.getSpans(start, end, URLSpan::class.java).isEmpty() &&
                text.getSpans(start, end, io.noties.markwon.core.spans.CodeSpan::class.java).isEmpty() &&
                text.getSpans(start, end, io.noties.markwon.core.spans.CodeBlockSpan::class.java).isEmpty()) {
                text.setSpan(URLSpan(link.url), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        val tasks = text.getSpans(0, text.length, TaskListSpan::class.java)
            .sortedBy { text.getSpanStart(it) }
        tasks.filter { (it as? SourceTaskSpan)?.completed ?: it.isDone }.forEach { task ->
            val start = text.getSpanStart(task)
            val end = text.toString().indexOf('\n', start).let {
                if (it < 0) text.getSpanEnd(task) else min(it, text.getSpanEnd(task))
            }
            text.setSpan(StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        textView.text = text
        textView.measure(
            View.MeasureSpec.makeMeasureSpec(renderWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        textView.layout(0, 0, renderWidth, textView.measuredHeight.coerceAtLeast(1))
        val rows = text.getSpans(0, text.length, io.noties.markwon.ext.tables.TableRowSpan::class.java)
        if (rows.isNotEmpty()) {
            textView.paint.color = textColor
            textView.paint.linkColor = accentColor
            val scratch = Bitmap.createBitmap(renderWidth, 1, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(scratch)
                rows.forEach { row ->
                    row.invalidator(null)
                    row.draw(canvas, text, text.getSpanStart(row), text.getSpanEnd(row), 0f, 0, 0, 1, textView.paint)
                }
            } finally { scratch.recycle() }
            textView.text = text
            textView.measure(View.MeasureSpec.makeMeasureSpec(renderWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            textView.layout(0, 0, renderWidth, textView.measuredHeight.coerceAtLeast(1))
        }
        return RenderedNote(textView, backgroundColor, density, tileByteLimit, tileHeightDp)
    }

    fun renderBitmap(markdown: String, width: Int, backgroundColor: Int, textColor: Int): Bitmap {
        return layoutNote(markdown, width, backgroundColor, textColor).let {
            it.drawSection(0, min(it.height, 1600))
        }
    }
}

data class NoteHitTarget(val bounds: Rect, val url: String? = null, val task: NoteTask? = null, val copyIndex: Int? = null)

internal fun <T> onMain(action: () -> T): T {
    if (Looper.myLooper() == Looper.getMainLooper()) return action()
    val task = FutureTask(action)
    Handler(Looper.getMainLooper()).post(task)
    return task.get(30, TimeUnit.SECONDS)
}

class RenderedNote internal constructor(
    private val view: TextView,
    internal val backgroundColor: Int,
    private val density: Float,
    tileByteLimit: Int,
    tileHeightDp: Int,
) {
    val width: Int get() = view.width
    val height: Int get() = view.height
    private val tileBleed = density.toInt().coerceAtLeast(1)
    val tileHeight = min((tileHeightDp * density).toInt(), tileByteLimit / (width * 4) - tileBleed * 2)
        .coerceAtLeast(1)
    val tileCount: Int get() = ceil(height.toDouble() / tileHeight).toInt()
    private val text = view.text as Spanned
    internal val renderedText: Spanned get() = text
    internal val textLayout: android.text.Layout get() = view.layout
    private val tasks = text.getSpans(0, text.length, SourceTaskSpan::class.java)
        .mapNotNull { span -> span.source?.let { span to it } }
    private val links = text.getSpans(0, text.length, URLSpan::class.java)
    private val embeds = text.getSpans(0, text.length, EmbedNoteSpan::class.java)
    private val codeBlocks = text.getSpans(0, text.length, CodeCopySpan::class.java).sortedBy { text.getSpanStart(it) }
    internal fun code(index: Int): String = codeBlocks[index].code
    private val taskImageRanges = text.toString().let { plain ->
        tasks.map { (span, task) ->
            val offset = text.getSpanStart(span)
            val end = plain.indexOf('\n', offset).let { if (it < 0) text.getSpanEnd(span) else it }
            Triple(task, view.layout.getLineTop(view.layout.getLineForOffset(offset)),
                view.layout.getLineBottom(view.layout.getLineForOffset((end - 1).coerceAtLeast(offset))))
        }
    }

    internal fun uncheckedMarkdown(markdown: String): String {
        val lines = markdown.split('\n').toMutableList()
        tasks.map { it.second }.distinctBy { it.line }.forEach { task ->
            lines[task.line] = lines[task.line].replaceRange(task.markerOffset, task.markerOffset + 1, " ")
        }
        return lines.joinToString("\n")
    }

    internal fun checkboxStateForTile(position: Int): String {
        val top = position * tileHeight
        val bottom = min(top + tileHeight, height)
        return taskImageRanges.filter { (_, start, end) -> end > top && start < bottom }
            .joinToString { (task, _, _) -> "${task.line}:${task.originalLine[task.markerOffset]}" }
    }

    fun drawTile(position: Int): Bitmap {
        val top = position * tileHeight
        return drawPixels(top - tileBleed, min(tileHeight, height - top) + tileBleed * 2)
    }

    internal fun drawInto(canvas: Canvas, x: Float, y: Float) {
        canvas.save()
        canvas.translate(x, y)
        view.draw(canvas)
        canvas.restore()
    }

    fun drawSection(top: Int, requestedHeight: Int): Bitmap {
        return drawPixels(top, min(requestedHeight, height - top).coerceAtLeast(1))
    }

    private fun drawPixels(top: Int, pixels: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, pixels,
            Bitmap.Config.ARGB_8888)
        bitmap.density = view.resources.displayMetrics.densityDpi
        Canvas(bitmap).apply {
            drawColor(backgroundColor)
            translate(0f, -top.toFloat())
            clipRect(0, top, width, top + bitmap.height)
            view.draw(this)
        }
        return bitmap
    }

    fun targetsForTile(position: Int): List<NoteHitTarget> {
        val top = position * tileHeight
        val bottom = min(top + tileHeight, height)
        val layout = view.layout
        val result = mutableListOf<NoteHitTarget>()
        embeds.forEach { span ->
            val offset = text.getSpanStart(span)
            val line = layout.getLineForOffset(offset)
            val x = layout.getPrimaryHorizontal(offset).toInt()
            val y = layout.getLineBaseline(line) - span.height
            val rect = Rect(span.linkBounds).apply { offset(x, y) }
            if (rect.bottom > top && rect.top < bottom) {
                rect.top = max(rect.top, top) - top
                rect.bottom = min(rect.bottom, bottom) - top
                result += NoteHitTarget(rect, url = span.url)
            }
        }
        codeBlocks.forEachIndexed { index, span ->
            val line = layout.getLineForOffset(text.getSpanStart(span))
            val y = layout.getLineTop(line)
            val end = layout.getLineBottom(line)
            if (end > top && y < bottom) {
                val right = (layout.getPrimaryHorizontal(text.getSpanStart(span)) +
                    span.getSize(Paint(), text, 0, 1, null)).toInt().coerceAtMost(width)
                result += NoteHitTarget(Rect((right - span.controlWidth).coerceAtLeast(0),
                    max(top, y) - top, right, min(bottom, end) - top), copyIndex = index)
            }
        }
        links.forEach { link ->
            val start = text.getSpanStart(link)
            val end = text.getSpanEnd(link)
            if (end <= start) return@forEach
            val first = layout.getLineForOffset(start)
            val last = layout.getLineForOffset(end - 1)
            for (line in first..last) {
                if (layout.getLineBottom(line) <= top || layout.getLineTop(line) >= bottom) continue
                val left = layout.getPrimaryHorizontal(max(start, layout.getLineStart(line)))
                val visibleEnd = layout.getLineVisibleEnd(line)
                val right = if (end >= visibleEnd) {
                    if (layout.getParagraphDirection(line) > 0) layout.getLineRight(line) else layout.getLineLeft(line)
                } else layout.getPrimaryHorizontal(end)
                result += NoteHitTarget(Rect(min(left, right).toInt().coerceAtLeast(0),
                    max(top, layout.getLineTop(line)) - top,
                    max(left, right).toInt().coerceAtMost(width),
                    min(bottom, layout.getLineBottom(line)) - top), url = link.url)
            }
        }
        tasks.forEach { (span, task) ->
            val line = layout.getLineForOffset(text.getSpanStart(span))
            if (layout.getLineBottom(line) <= top || layout.getLineTop(line) >= bottom) return@forEach
            val margin = span.getLeadingMargin(true)
            val rtl = layout.getParagraphDirection(line) < 0
            val edge = layout.getPrimaryHorizontal(text.getSpanStart(span)).toInt()
            result += NoteHitTarget(Rect(
                (if (rtl) edge else edge - margin).coerceAtLeast(0),
                max(top, layout.getLineTop(line)) - top,
                (if (rtl) edge + margin else edge).coerceAtMost(width),
                min(bottom, layout.getLineBottom(line)) - top), task = task)
        }
        return result.filter { it.bounds.width() > 0 && it.bounds.height() > 0 }
    }
}

private class TextLineSpacing : LineHeightSpan {
    private var previousAscent = 0
    private var previousTop = 0
    private var previousDescent = 0
    private var previousBottom = 0
    private var previousExtra = 0
    override fun chooseHeight(text: CharSequence, start: Int, end: Int, spanstartv: Int, v: Int, fm: Paint.FontMetricsInt) {
        if ((text as? Spanned)?.getSpans(start, end, ReplacementSpan::class.java)?.isNotEmpty() == true) {
            previousExtra = 0
            return
        }
        if (previousExtra != 0 && fm.ascent == previousAscent && fm.top == previousTop && fm.descent == previousDescent) {
            fm.descent -= previousExtra
            if (fm.bottom == previousDescent) fm.bottom = previousBottom
        }
        val extra = ((fm.descent - fm.ascent) * 0.25f).toInt()
        previousAscent = fm.ascent
        previousTop = fm.top
        previousBottom = fm.bottom
        previousExtra = extra
        fm.descent += extra
        fm.bottom = max(fm.bottom, fm.descent)
        previousDescent = fm.descent
    }
}
