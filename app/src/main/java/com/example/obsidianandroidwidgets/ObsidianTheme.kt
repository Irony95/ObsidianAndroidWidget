package com.example.obsidianandroidwidgets

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.JsonReader
import android.util.JsonToken
import androidx.annotation.WorkerThread
import java.io.IOException
import java.io.StringReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ObsidianTheme {
    const val DEFAULT_ACCENT_COLOR = 0xFF8A5CF5.toInt()
    private const val MAX_APPEARANCE_BYTES = 64 * 1024
    private const val MAX_CHILDREN_TO_SCAN = 4096
    private val HEX_COLOR = Regex("#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})")
    private const val APPEARANCE_FILES_PREFS = "obsidianAppearanceFiles"
    fun hasAppearanceFile(context: Context, vault: String): Boolean =
        context.getSharedPreferences(APPEARANCE_FILES_PREFS, Context.MODE_PRIVATE).contains(vault)

    @WorkerThread
    fun accentColor(
        context: Context,
        vaultUriString: String?,
        fallbackColor: Int,
    ): Int {
        if (vaultUriString.isNullOrBlank()) return fallbackColor

        return try {
            val selectedFile = context.getSharedPreferences(APPEARANCE_FILES_PREFS, Context.MODE_PRIVATE)
                .getString(vaultUriString, null)
            if (selectedFile != null) {
                val selectedColor = runCatching {
                    readBoundedJson(context.contentResolver, Uri.parse(selectedFile))?.let(::parseAccentColor)
                }.onFailure { if (it is CancellationException) throw it }.getOrNull()
                if (selectedColor != null) return selectedColor
            }
            val treeUri = Uri.parse(vaultUriString)
            if (treeUri.scheme != ContentResolver.SCHEME_CONTENT ||
                !DocumentsContract.isTreeUri(treeUri)
            ) {
                return fallbackColor
            }

            val resolver = context.contentResolver
            val json = readConfigFile(resolver, treeUri, "appearance.json") ?: return fallbackColor
            parseAccentColor(json) ?: fallbackColor
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            fallbackColor
        } catch (_: RuntimeException) {
            fallbackColor
        }
    }

    @WorkerThread
    fun rememberAppearanceFile(context: Context, vault: String, file: Uri): Int? {
        if (vault.isBlank()) return null
        val color = runCatching { readBoundedJson(context.contentResolver, file)?.let(::parseAccentColor) }
            .onFailure { if (it is CancellationException) throw it }.getOrNull() ?: return null
        context.getSharedPreferences(APPEARANCE_FILES_PREFS, Context.MODE_PRIVATE)
            .edit().putString(vault, file.toString()).apply()
        return color
    }

    suspend fun readAccentColor(
        context: Context,
        vaultUriString: String?,
        fallbackColor: Int,
    ): Int = withContext(Dispatchers.IO) {
        val saved = accentColor(context, vaultUriString, 0)
        val cache = context.getSharedPreferences("obsidianAccentCache", Context.MODE_PRIVATE)
        val key = vaultUriString.orEmpty()
        cache.edit().apply {
            if (saved == 0) remove(key) else putInt(key, saved)
        }.apply()
        if (saved == 0) fallbackColor else saved
    }

    fun cachedAccentColor(context: Context, vaultUriString: String?, fallbackColor: Int): Int =
        context.getSharedPreferences("obsidianAccentCache", Context.MODE_PRIVATE)
            .getInt(vaultUriString.orEmpty(), fallbackColor)

    private fun readConfigFile(resolver: ContentResolver, tree: Uri, name: String): String? {
        val root = DocumentsContract.getTreeDocumentId(tree)
        val direct = DocumentsContract.buildDocumentUriUsingTree(tree, root.trimEnd('/') + "/.obsidian/$name")
        val json = runCatching { readBoundedJson(resolver, direct) }
            .onFailure { if (it is CancellationException) throw it }.getOrNull()
        if (json != null) return json
        val config = findChildDocumentId(resolver, tree, root, ".obsidian", true) ?: return null
        val file = findChildDocumentId(resolver, tree, config, name, false) ?: return null
        return readBoundedJson(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, file))
    }

    private fun findChildDocumentId(
        resolver: ContentResolver,
        treeUri: Uri,
        parentId: String,
        name: String,
        directory: Boolean,
    ): String? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )

        return resolver.query(childrenUri, columns, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            var scanned = 0
            while (scanned < MAX_CHILDREN_TO_SCAN && cursor.moveToNext()) {
                scanned++
                val isDirectory = cursor.getString(mimeColumn) == DocumentsContract.Document.MIME_TYPE_DIR
                if (cursor.getString(nameColumn) == name && isDirectory == directory) {
                    return@use cursor.getString(idColumn)?.takeIf { it.isNotBlank() }
                }
            }
            null
        }
    }

    private fun readBoundedJson(resolver: ContentResolver, uri: Uri): String? {
        return resolver.openInputStream(uri)?.use { input ->
            val bytes = ByteArray(MAX_APPEARANCE_BYTES + 1)
            var size = 0
            while (size < bytes.size) {
                val count = input.read(bytes, size, bytes.size - size)
                if (count < 0) return@use String(bytes, 0, size, Charsets.UTF_8)
                if (count == 0) return@use null
                size += count
            }
            null
        }
    }

    private fun parseAccentColor(json: String): Int? {
        return JsonReader(StringReader(json)).use { reader ->
            reader.isLenient = false
            reader.beginObject()
            var color: Int? = null
            while (reader.hasNext()) {
                if (reader.nextName() == "accentColor") {
                    color = if (reader.peek() == JsonToken.STRING) {
                        parseHexColor(reader.nextString())
                    } else {
                        reader.skipValue()
                        null
                    }
                } else {
                    reader.skipValue()
                }
            }
            reader.endObject()
            if (reader.peek() == JsonToken.END_DOCUMENT) color else null
        }
    }

    private fun parseHexColor(value: String): Int? {
        if (!HEX_COLOR.matches(value)) return null
        val hex = value.substring(1)
        val rgb = if (hex.length == 3) hex.map { "$it$it" }.joinToString("") else hex
        return 0xFF000000.toInt() or rgb.toInt(16)
    }
}
