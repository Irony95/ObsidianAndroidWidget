package com.example.obsidianandroidwidgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Image
import org.commonmark.parser.Parser
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

internal object AttachmentCache {
    fun destinations(markdown: String): List<String> = mutableListOf<String>().also { result ->
        Parser.builder().build().parse(markdown).accept(object : AbstractVisitor() {
            override fun visit(image: Image) { result += image.destination }
        })
    }
    fun webEmbed(value: String): Boolean = android.net.Uri.parse(value).host?.lowercase()?.removePrefix("www.") in
        setOf("youtube.com", "youtu.be", "twitter.com", "x.com", "mobile.twitter.com")

    fun remoteImages(context: Context, markdown: String, images: JSONObject) {
        destinations(markdown).distinct().filter { it.startsWith("https://") && !webEmbed(it) }.take(8).forEach { url ->
            if (images.has(url)) return@forEach
            runCatching {
                val directory = File(context.noBackupFilesDir, "vault-images").apply { mkdirs() }
                val metadata = File(context.cacheDir, "remote-image-${NoteTasks.fingerprint(url)}.json")
                val cached = runCatching { JSONObject(metadata.readText()) }.getOrNull()
                if (cached != null && System.currentTimeMillis() - metadata.lastModified() < 3600000 &&
                    File(directory, cached.getString("image")).exists()) {
                    images.put(url, cached.getString("image")); return@runCatching
                }
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 8000
                try {
                    check(connection.responseCode == 200 && connection.contentType.orEmpty().startsWith("image/"))
                    val bytes = connection.inputStream.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            check(output.size() + count <= 16 * 1024 * 1024)
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    val key = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                    File(directory, key).writeBytes(bytes)
                    images.put(url, key)
                    metadata.writeText(JSONObject().put("image", key).toString())
                } finally { connection.disconnect() }
            }
        }
    }

    fun pdfPage(context: Context, key: String, pageNumber: Int): String? = runCatching {
        val directory = File(context.noBackupFilesDir, "vault-images")
        val result = NoteTasks.fingerprint("pdf:$key:$pageNumber")
        val output = File(directory, result)
        if (!output.exists()) {
            PdfRenderer(ParcelFileDescriptor.open(File(directory, key), ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                renderer.openPage(pageNumber.coerceIn(0, renderer.pageCount - 1)).use { page ->
                    val height = (1024L * page.height / page.width.coerceAtLeast(1)).toInt().coerceIn(1, 2000)
                    val bitmap = Bitmap.createBitmap(1024, height, Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    } finally { bitmap.recycle() }
                }
            }
        }
        result
    }.getOrNull()
}
