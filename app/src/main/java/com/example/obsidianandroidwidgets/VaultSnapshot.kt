package com.example.obsidianandroidwidgets

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import org.json.JSONObject
import java.io.File

class VaultSnapshot(val data: JSONObject = JSONObject()) {
    val vault: String get() = data.optString("vault")
    fun entry(target: String): JSONObject? = data.optJSONObject("entries")?.optJSONObject(target.substringBefore('|').substringBefore('#'))
    fun noteUrl(target: String): String {
        val value = target.substringBefore('|')
        val path = entry(value)?.optString("path")?.ifBlank { null } ?: value.substringBefore('#').ifBlank { data.optString("notePath") }
        return Uri.Builder().scheme("obsidian").authority("open")
        .appendQueryParameter("vault", vault)
        .appendQueryParameter("file", path + if ('#' in value) "#${value.substringAfter('#')}" else "")
        .build().toString()
    }

    companion object {
        val wiki = Regex("(!?)\\[\\[([^]\\n]+)]]")
        private val image = Regex("(?i)\\.(png|jpe?g|gif|webp|bmp)$")
        fun parse(value: String): VaultSnapshot = VaultSnapshot(runCatching { JSONObject(value) }.getOrDefault(JSONObject()))

        fun capture(context: Context, vaultUri: String, vaultName: String, notePath: String, markdown: String): String {
            val entries = JSONObject()
            val result = JSONObject().put("vault", vaultName).put("notePath", notePath).put("entries", entries)
            val remote = JSONObject()
            AttachmentCache.remoteImages(context, markdown, remote)
            result.put("images", remote)
            fun localImages(source: String) = AttachmentCache.destinations(source).filter {
                !it.startsWith("data:") && !it.contains("://")
            }.joinToString("\n") { "![[${Uri.decode(it).removePrefix("./")}]]" }
            val initial = markdown + "\n" + localImages(markdown)
            if (!wiki.containsMatchIn(initial) || vaultUri.isBlank()) return result.toString()
            val tree = Uri.parse(vaultUri)
            val root = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return result.toString()
            data class Document(val id: String, val path: String)
            val files = mutableListOf<Document>()
            val folders = java.util.ArrayDeque<Document>().apply { add(Document(root, "")) }
            var visited = 0
            while (folders.isNotEmpty() && visited < 10000) {
                val folder = folders.removeFirst()
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, folder.id)
                runCatching {
                    context.contentResolver.query(children, arrayOf("document_id", "_display_name", "mime_type"), null, null, null)?.use { cursor ->
                        while (cursor.moveToNext() && visited++ < 10000) {
                            val name = cursor.getString(1)
                            if (name.startsWith('.')) continue
                            val doc = Document(cursor.getString(0), (folder.path + "/" + name).trimStart('/'))
                            if (cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                                if (doc.path.count { it == '/' } < 20) folders.add(doc)
                            } else files += doc
                        }
                    }
                }
            }
            fun resolve(target: String, parent: String): Document? {
                val path = target.removePrefix("/").replace('\\', '/')
                val candidates = listOf(path, "$path.md", "$path.markdown")
                val local = parent.substringBeforeLast('/', "").let { if (it.isBlank()) "" else "$it/" }
                return files.firstOrNull { it.path in candidates.map { value -> local + value } }
                    ?: files.firstOrNull { it.path in candidates }
                    ?: files.filter { it.path.substringAfterLast('/') in candidates }.singleOrNull()
            }
            val directory = File(context.noBackupFilesDir, "vault-images").apply { mkdirs() }
            var capturedBytes = 0
            fun collect(source: String, parent: String, depth: Int) {
                if (depth > 3) return
                AttachmentCache.remoteImages(context, source, remote)
                wiki.findAll(source + "\n" + localImages(source)).take(48).forEach { match ->
                    val target = match.groupValues[2].replace("\\|", "|").substringBefore('|').substringBefore('#')
                    val previous = entries.optJSONObject(target)
                    val page = Regex("(?:^|&)page=(\\d+)").find(match.groupValues[2].substringAfter('#', ""))
                        ?.groupValues?.get(1)?.toIntOrNull()?.minus(1)?.coerceAtLeast(0) ?: 0
                    if (previous?.has("pdf") == true) {
                        val pages = previous.getJSONObject("pages")
                        if (!pages.has(page.toString())) AttachmentCache.pdfPage(context, previous.getString("pdf"), page)?.let { pages.put(page.toString(), it) }
                        return@forEach
                    }
                    if ((previous != null && (match.groupValues[1] != "!" || previous.has("markdown") || previous.has("image"))) || entries.length() >= 48 || capturedBytes > 4 * 1024 * 1024) return@forEach
                    val document = resolve(target, parent) ?: return@forEach
                    val item = JSONObject().put("path", document.path).put("title", document.path.substringAfterLast('/').removeSuffix(".md"))
                    entries.put(target, item)
                    if (match.groupValues[1] != "!") return@forEach
                    runCatching {
                        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, document.id)
                        val limit = if (image.containsMatchIn(document.path) || document.path.endsWith(".pdf", true)) 16 * 1024 * 1024 else 1024 * 1024
                        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                check(output.size() + count <= limit) { "Embed is too large" }
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        } ?: return@runCatching
                        capturedBytes += bytes.size
                        if (image.containsMatchIn(document.path) || document.path.endsWith(".pdf", true)) {
                            val key = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                            val file = File(directory, key)
                            if (!file.exists()) file.writeBytes(bytes)
                            if (document.path.endsWith(".pdf", true)) {
                                item.put("pdf", key)
                                val pages = JSONObject()
                                AttachmentCache.pdfPage(context, key, page)?.let { pages.put(page.toString(), it) }
                                item.put("pages", pages)
                            } else item.put("image", key)
                        } else if (document.path.endsWith(".md", true) || document.path.endsWith(".markdown", true)) {
                            val text = bytes.toString(Charsets.UTF_8)
                            item.put("markdown", text)
                            collect(text, document.path, depth + 1)
                        }
                    }
                }
            }
            collect(markdown, notePath, 0)
            return result.toString()
        }
    }
}
