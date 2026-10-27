package com.example.obsidianandroidwidgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.style.ForegroundColorSpan
import android.text.style.ReplacementSpan
import android.text.style.URLSpan
import android.util.Base64
import android.util.LruCache
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonVisitor
import org.commonmark.node.*
import java.io.File

internal class VaultSyntaxPlugin(private val context: Context, private val snapshot: VaultSnapshot,
    private val width: Int, private val background: Int, private val color: Int,
    private val accent: Int, private val depth: Int) : AbstractMarkwonPlugin() {
    private val syntax = Regex("!?\\[\\[[^]\\n]+]]|(?<![\\w/#])#[\\p{L}\\p{N}_/-]+")

    override fun beforeRender(node: Node) {
        val leaves = mutableListOf<Node>()
        node.accept(object : AbstractVisitor() {
            override fun visit(text: Text) {
                if (generateSequence(text.parent) { it.parent }.none { it is Link || it is Image }) leaves += text
            }
            override fun visit(image: Image) {
                leaves += image
            }
        })
        leaves.forEach { leaf ->
            val available = markdownContentWidth(leaf, width, (24 * context.resources.displayMetrics.density).toInt(),
                (12 * context.resources.displayMetrics.density).toInt(), (4 * context.resources.displayMetrics.density).toInt())
            if (leaf is Image) {
                val destination = leaf.destination
                val alt = StringBuilder().also { label -> leaf.accept(object : AbstractVisitor() {
                    override fun visit(text: Text) { label.append(text.literal) }
                }) }.toString()
                val key = if (destination.startsWith("data:image/")) destination else
                    snapshot.data.optJSONObject("images")?.optString(destination)?.ifBlank { null }
                        ?: snapshot.entry(android.net.Uri.decode(destination).removePrefix("./"))?.optString("image")?.ifBlank { null }
                val span = key?.let { imageSpan(it, alt.substringAfterLast('|', ""), available) }
                val fallback = if (destination.contains("://")) destination else snapshot.noteUrl(android.net.Uri.decode(destination))
                leaf.insertBefore(if (span != null && !AttachmentCache.webEmbed(destination)) MediaNode(span) else
                    Link(fallback, null).apply { appendChild(Text(alt.substringBefore('|').ifBlank { destination })) })
                leaf.unlink()
            } else if (leaf is Text) {
                var offset = 0
                syntax.findAll(leaf.literal).forEach { match ->
                    if (match.range.first > offset) leaf.insertBefore(Text(leaf.literal.substring(offset, match.range.first)))
                    val token = match.value
                    val replacement = when {
                        token.startsWith('#') -> TagNode(token)
                        token.startsWith("![[") -> embed(token.substring(3, token.length - 2), available)
                        else -> {
                            val target = token.substring(2, token.length - 2)
                            val label = target.substringAfter('|', target.substringBefore('|').removePrefix("#").replace("#", " > "))
                            Link(snapshot.noteUrl(target), null).apply { appendChild(Text(label)) }
                        }
                    }
                    leaf.insertBefore(replacement)
                    offset = match.range.last + 1
                }
                if (offset > 0) {
                    if (offset < leaf.literal.length) leaf.insertBefore(Text(leaf.literal.substring(offset)))
                    leaf.unlink()
                }
            }
        }
    }

    private fun embed(target: String, available: Int = width): Node {
        val entry = snapshot.entry(target)
        if (entry?.has("image") == true) imageSpan(entry.getString("image"), target.substringAfterLast('|', ""), available)?.let { return MediaNode(it) }
        if (entry?.has("pdf") == true) {
            val page = Regex("(?:^|&)page=(\\d+)").find(target.substringAfter('#', ""))?.groupValues?.get(1)?.toIntOrNull()?.minus(1) ?: 0
            val key = entry.optJSONObject("pages")?.optString(page.coerceAtLeast(0).toString()).orEmpty()
            if (key.isNotBlank()) imageSpan(key, available = available)?.let { return MediaNode(it) }
        }
        if (entry != null && !entry.has("markdown")) return Link(snapshot.noteUrl(target), null).apply {
            appendChild(Text(target.substringBefore('|')))
        }
        if (entry == null && Regex("(?i)\\.(png|jpe?g|gif|webp|svg|bmp|pdf|mp3|m4a|wav|ogg|flac|mp4|webm|canvas)(?:#.*)?$")
                .containsMatchIn(target.substringBefore('|'))) {
            return Link(snapshot.noteUrl(target), null).apply { appendChild(Text("Attachment unavailable: ${target.substringBefore('|')}")) }
        }
        val rawContent = entry?.optString("markdown").orEmpty()
        val fragment = target.substringBefore('|').substringAfter('#', "")
        val content = if (rawContent.isNotBlank()) EmbedSelection.select(rawContent, fragment) else ""
        val title = entry?.optString("title")?.ifBlank { null } ?: target.substringBefore('|')
        val inset = (10 * context.resources.displayMetrics.density).toInt()
        val child = BitmapRenderer(context).layoutNote(
            if (depth < 3 && content.isNotBlank())
                ObsidianSourcePlugin.withoutFrontmatter(content) else "Embedded note unavailable.",
            (available - inset * 2).coerceAtLeast(1), background, color, accent,
            vaultSnapshot = snapshot, embedDepth = depth + 1, interactive = false)
        return MediaNode(EmbedNoteSpan(context, snapshot.noteUrl(target), available, child,
            if (fragment.isBlank()) title else null, accent, color))
    }

    private fun imageSpan(source: String, dimensions: String = "", available: Int = width): ReplacementSpan? {
        val key = NoteTasks.fingerprint("$width|$source")
        fun bytes(): ByteArray? = runCatching {
            if (source.startsWith("data:image/")) Base64.decode(source.substringAfter("base64,"), Base64.DEFAULT)
            else if (source.matches(Regex("[a-f0-9]{64}"))) File(context.noBackupFilesDir, "vault-images/$source").readBytes()
            else null
        }.getOrNull()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        bytes()?.let { BitmapFactory.decodeByteArray(it, 0, it.size, bounds) }
        if (bounds.outWidth <= 0) return null
        val requested = Regex("^(\\d+)(?:x(\\d+))?$").matchEntire(dimensions)
        val requestedWidth = requested?.groupValues?.get(1)?.toFloatOrNull()?.times(context.resources.displayMetrics.density)
        val imageWidth = requestedWidth?.toInt()?.coerceIn(1, available) ?: available
        val imageHeight = (requested?.groupValues?.get(2)?.toFloatOrNull()?.let {
            it * context.resources.displayMetrics.density * imageWidth / (requestedWidth ?: imageWidth.toFloat())
        }?.toInt() ?: (imageWidth.toLong() * bounds.outHeight / bounds.outWidth).toInt()).coerceIn(1, width * 5)
        return object : SizedMediaSpan(imageWidth, imageHeight) {
            override fun paint(canvas: Canvas, x: Float, top: Float) {
                var bitmap = images.get(key)
                if (bitmap == null) {
                    val options = BitmapFactory.Options()
                    while (bounds.outWidth / (options.inSampleSize.coerceAtLeast(1) * 2) > width ||
                        bounds.outHeight / (options.inSampleSize.coerceAtLeast(1) * 2) > 2000) {
                        options.inSampleSize = options.inSampleSize.coerceAtLeast(1) * 2
                    }
                    bitmap = bytes()?.let { BitmapFactory.decodeByteArray(it, 0, it.size, options) }
                    if (bitmap != null) images.put(key, bitmap)
                }
                bitmap?.let { canvas.drawBitmap(it, null, RectF(x, top, x + this.width, top + height), imagePaint) }
            }
        }
    }

    override fun configureVisitor(builder: MarkwonVisitor.Builder) {
        builder.on(TagNode::class.java) { visitor, node ->
            val start = visitor.length()
            visitor.builder().append(node.label)
            visitor.setSpans(start, arrayOf(ForegroundColorSpan(accent), WidgetHighlightSpan((accent and 0x00FFFFFF) or 0x22000000)))
            visitor.setSpans(start, object : URLSpan(android.net.Uri.Builder().scheme("obsidian").authority("search")
                .appendQueryParameter("vault", snapshot.vault).appendQueryParameter("query", "tag:${node.label}").build().toString()) {
                override fun updateDrawState(ds: android.text.TextPaint) { ds.color = accent; ds.isUnderlineText = false }
            })
        }
        builder.on(MediaNode::class.java) { visitor, node ->
            visitor.ensureNewLine()
            val start = visitor.length()
            visitor.builder().append('\uFFFC')
            visitor.setSpans(start, node.span)
        }
    }

    private class TagNode(val label: String) : CustomNode()
    private class MediaNode(val span: ReplacementSpan) : CustomNode()
    private abstract class SizedMediaSpan(val width: Int, val height: Int) : ReplacementSpan() {
        override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
            fm?.apply { ascent = -height; top = ascent; descent = 0; bottom = 0 }
            return width
        }
        override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float,
            top: Int, y: Int, bottom: Int, paint: Paint) = paint(canvas, x, (y - height).toFloat())
        abstract fun paint(canvas: Canvas, x: Float, top: Float)
    }
    companion object {
        private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val images = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
        }
    }
}
