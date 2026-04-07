package com.example.obsidianandroidwidgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tables.TableTheme
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import kotlin.math.sqrt

class BitmapRenderer(private val context: Context) {
    companion object {
        private const val IMAGE_PADDING_PX = 0
        private const val MAX_BITMAP_BYTES = 16 * 1024 * 1024
        private const val HIGHLIGHT_BACKGROUND_COLOR = 0xFFFFD54F.toInt()
        private const val TABLE_CELL_PADDING_DP = -4
        private const val TABLE_CELL_HORIZONTAL_INSET = "\u2005"
        private val HEADING_TEXT_MULTIPLIERS = floatArrayOf(2.0f, 1.6f, 1.37f, 1.25f, 1.12f, 1.0f)
        private val LIST_LINE_REGEX = Regex("^\\s*([-*+]|\\d+[.)])\\s+.+$")
        private val CHECKED_TASK_LINE_REGEX = Regex("""^(\s*(?:[-*+]|\d+[.)])\s+\[(?:x|X)\]\s+)(.+)$""")
        private val HIGHLIGHT_REGEX = Regex("==(?=\\S)(.+?)(?<=\\S)==")
        private val TABLE_SEPARATOR_REGEX = Regex("""^\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?$""")
        private const val HIGHLIGHT_START_TOKEN = "\uE000"
        private const val HIGHLIGHT_END_TOKEN = "\uE001"
    }

    private val tableTheme = TableTheme.buildWithDefaults(context)
        .tableCellPadding(dpToPx(TABLE_CELL_PADDING_DP))
        .tableBorderColor(android.graphics.Color.TRANSPARENT)
        .build()

    private val markwon: Markwon = Markwon.builder(context)
        .usePlugin(object : AbstractMarkwonPlugin() {
            override fun configureTheme(builder: MarkwonTheme.Builder) {
                builder
                    .headingBreakHeight(0)
                    .headingTextSizeMultipliers(HEADING_TEXT_MULTIPLIERS)
            }
        })
        .usePlugin(TaskListPlugin.create(context))
        .usePlugin(TablePlugin.create(tableTheme))
        .usePlugin(ImagesPlugin.create())
        .usePlugin(SoftBreakAddsNewLinePlugin.create())
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(HtmlPlugin.create())
        .build()

    fun renderBitmap(
        markdown: String,
        width: Int,
        backgroundColor: Int,
        textColor: Int,
    ): Bitmap {
        val paddedWidth = (width - (IMAGE_PADDING_PX * 2)).coerceAtLeast(1)
        val textView = TextView(context).apply {
            setTextColor(textColor)
            setBackgroundColor(backgroundColor)
            textSize = 16f
            this.width = paddedWidth
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
            setLineSpacing(0f, 1.08f)
        }

        markwon.setMarkdown(textView, preprocessMarkdown(markdown))
        applyHighlightSpans(textView)

        val widthSpec = View.MeasureSpec.makeMeasureSpec(paddedWidth, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        textView.measure(widthSpec, heightSpec)

        val measuredHeight = textView.measuredHeight.coerceAtLeast(1)
        textView.layout(0, 0, paddedWidth, measuredHeight)

        val baseBitmap = Bitmap.createBitmap(paddedWidth, measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(baseBitmap)
        canvas.drawColor(backgroundColor)
        textView.draw(canvas)

        val bitmapBytes = baseBitmap.allocationByteCount
        if (bitmapBytes <= MAX_BITMAP_BYTES) {
            return baseBitmap
        }

        val scale = sqrt(MAX_BITMAP_BYTES.toDouble() / bitmapBytes.toDouble())
            .toFloat()
            .coerceIn(0.35f, 1f)

        val scaledWidth = (baseBitmap.width * scale).toInt().coerceAtLeast(1)
        val scaledHeight = (baseBitmap.height * scale).toInt().coerceAtLeast(1)
        val scaledBitmap = Bitmap.createScaledBitmap(baseBitmap, scaledWidth, scaledHeight, true)
        if (scaledBitmap != baseBitmap) {
            baseBitmap.recycle()
        }
        return scaledBitmap
    }

    private fun preprocessMarkdown(markdown: String): String {
        val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val result = mutableListOf<String>()
        var insideFence = false
        var index = 0

        while (index < lines.size) {
            val line = lines[index]
            val trimmed = line.trimEnd()

            if (isFenceDelimiter(trimmed)) {
                insideFence = !insideFence
                result += trimmed
                index++
                continue
            }

            if (
                !insideFence &&
                trimmed.isNotBlank() &&
                isTopLevelParagraphLine(line) &&
                followsTopLevelList(result)
            ) {
                result += ""
            }

            result += if (insideFence) {
                trimmed
            } else {
                applyInlineMarkdownFixes(trimmed)
            }
            index++
        }

        return result.joinToString("\n")
    }

    private fun isHeadingLine(line: String): Boolean {
        return line.matches(Regex("^#{1,6}\\s+.+$"))
    }

    private fun isFenceDelimiter(line: String): Boolean {
        return line.matches(Regex("^(```|~~~).*$"))
    }

    private fun followsTopLevelList(lines: List<String>): Boolean {
        val previousLine = lines.lastOrNull { it.isNotBlank() } ?: return false
        return LIST_LINE_REGEX.matches(previousLine)
    }

    private fun isTopLevelParagraphLine(line: String): Boolean {
        val trimmed = line.trimEnd()
        return trimmed.isNotBlank() &&
            !line.startsWith(" ") &&
            !line.startsWith("\t") &&
            !LIST_LINE_REGEX.matches(trimmed) &&
            !isHeadingLine(trimmed)
    }

    private fun applyInlineMarkdownFixes(line: String): String {
        return rewriteTableCellHorizontalInset(
            replaceHighlightSyntax(rewriteCheckedTaskLine(line))
        )
    }

    private fun rewriteCheckedTaskLine(line: String): String {
        val match = CHECKED_TASK_LINE_REGEX.matchEntire(line) ?: return line
        val content = match.groupValues[2]
        val trimmedContent = content.trim()
        if (trimmedContent.isEmpty() || (trimmedContent.startsWith("~~") && trimmedContent.endsWith("~~"))) {
            return line
        }

        return "${match.groupValues[1]}~~$content~~"
    }

    private fun rewriteTableCellHorizontalInset(line: String): String {
        if (!looksLikeMarkdownTableContent(line)) {
            return line
        }

        val leadingPipe = line.startsWith("|")
        val trailingPipe = line.endsWith("|")
        val rawCells = line.split('|')
        val startIndex = if (leadingPipe) 1 else 0
        val endExclusive = if (trailingPipe) rawCells.size - 1 else rawCells.size
        val cells = rawCells.subList(startIndex, endExclusive).map { cell ->
            val trimmed = cell.trim()
            if (trimmed.isEmpty()) {
                ""
            } else {
                "$TABLE_CELL_HORIZONTAL_INSET$trimmed$TABLE_CELL_HORIZONTAL_INSET"
            }
        }

        return buildString {
            if (leadingPipe) {
                append('|')
            }
            append(cells.joinToString("|"))
            if (trailingPipe) {
                append('|')
            }
        }
    }

    private fun looksLikeMarkdownTableContent(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.count { it == '|' } >= 2 && !TABLE_SEPARATOR_REGEX.matches(trimmed)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    private fun replaceHighlightSyntax(line: String): String {
        return HIGHLIGHT_REGEX.replace(line) {
            "$HIGHLIGHT_START_TOKEN${it.groupValues[1]}$HIGHLIGHT_END_TOKEN"
        }
    }

    private fun applyHighlightSpans(textView: TextView) {
        val spannable = SpannableStringBuilder.valueOf(textView.text)
        var searchFrom = 0

        while (searchFrom < spannable.length) {
            val currentText = spannable.toString()
            val startIndex = currentText.indexOf(HIGHLIGHT_START_TOKEN, searchFrom)
            if (startIndex == -1) {
                break
            }

            val markerEnd = startIndex + HIGHLIGHT_START_TOKEN.length
            val endIndex = currentText.indexOf(HIGHLIGHT_END_TOKEN, markerEnd)
            if (endIndex == -1) {
                spannable.delete(startIndex, markerEnd)
                searchFrom = startIndex
                continue
            }

            spannable.delete(endIndex, endIndex + HIGHLIGHT_END_TOKEN.length)
            spannable.delete(startIndex, markerEnd)

            val spanEnd = endIndex - HIGHLIGHT_START_TOKEN.length
            if (spanEnd > startIndex) {
                val highlightTextColor = if (ColorUtils.calculateLuminance(HIGHLIGHT_BACKGROUND_COLOR) < 0.5) {
                    android.graphics.Color.WHITE
                } else {
                    android.graphics.Color.BLACK
                }

                spannable.setSpan(
                    BackgroundColorSpan(HIGHLIGHT_BACKGROUND_COLOR),
                    startIndex,
                    spanEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    ForegroundColorSpan(highlightTextColor),
                    startIndex,
                    spanEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            searchFrom = spanEnd
        }

        textView.text = spannable
    }
}
