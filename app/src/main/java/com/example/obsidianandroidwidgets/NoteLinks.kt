package com.example.obsidianandroidwidgets

import android.net.Uri
import androidx.core.util.PatternsCompat
import java.util.Locale

internal object NoteLinks {
    private val vaultExtensions = setOf("md", "markdown", "canvas", "base", "pdf", "png", "jpg", "jpeg", "gif", "svg", "webp")

    fun resolve(destination: String, vault: String, file: String): Uri {
        val raw = destination.trim()
        require(raw.isNotEmpty()) { "Empty link" }
        val link = Uri.parse(raw).normalizeScheme()
        if (link.scheme in listOf("http", "https", "mailto", "tel", "obsidian")) return link
        require(link.scheme == null) { "Unsupported link" }
        if (raw.startsWith("//")) return Uri.parse("https:$raw")

        val host = raw.substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':')
        val extension = Uri.decode(link.path.orEmpty()).substringAfterLast('.', "").lowercase(Locale.ROOT)
        val vaultFile = '/' !in link.path.orEmpty() && extension in vaultExtensions
        if (!raw.startsWith('.') && !vaultFile &&
            PatternsCompat.DOMAIN_NAME.matcher(host).matches() && host.contains('.') &&
            PatternsCompat.WEB_URL.matcher("https://$raw").matches()) {
            return Uri.parse("https://$raw")
        }

        val decoded = Uri.decode(raw)
        if (decoded.startsWith('#')) return PageWidget.noteOpenUri(vault, file + decoded)
        val parent = if (decoded.startsWith('/')) "" else file.substringBeforeLast('/', "")
        val parts = mutableListOf<String>()
        "$parent/$decoded".split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts += part
            }
        }
        return PageWidget.noteOpenUri(vault, parts.joinToString("/"))
    }
}
