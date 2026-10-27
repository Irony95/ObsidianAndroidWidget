package com.example.obsidianandroidwidgets

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class NoteTileProvider : ContentProvider() {
    override fun onCreate() = true
    override fun getType(uri: Uri) = "image/png"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Read only")
        val parts = uri.pathSegments
        val key = parts.getOrNull(0).orEmpty()
        val position = parts.getOrNull(1)?.toIntOrNull() ?: throw FileNotFoundException()
        if (!key.matches(Regex("[a-f0-9]{64}")) || position < 0) throw FileNotFoundException()
        val app = requireNotNull(context)
        val source = NoteTileStore.sourceKey(app, key)
        val file = cacheTile(app, source, position)
        for (next in position + 1..position + 3) {
            val pendingKey = "$source/$next"
            if (pending.add(pendingKey)) {
                try {
                    prefetch.execute {
                        try { cacheTile(app, source, next) } catch (_: Exception) { /* Demand loading retries. */ }
                        finally { pending.remove(pendingKey) }
                    }
                } catch (_: java.util.concurrent.RejectedExecutionException) { pending.remove(pendingKey) }
            }
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun cacheTile(app: Context, key: String, position: Int): File {
        val directory = File(app.cacheDir, "note-tiles/$key").apply { mkdirs() }
        val file = File(directory, "$position.png")
        if (!file.exists()) {
            val note = NoteTileStore.load(app, key)
            if (position >= note.tileCount) throw FileNotFoundException()
            val bitmap = onMain { note.drawTile(position) }
            try {
                synchronized(NoteTileStore) {
                    if (!file.exists()) {
                        val cache = AtomicFile(file)
                        val output = cache.startWrite()
                        try {
                            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                            cache.finishWrite(output)
                        } catch (error: Exception) {
                            cache.failWrite(output)
                            throw error
                        }
                    }
                }
            } catch (error: Exception) {
                throw error
            } finally {
                bitmap.recycle()
            }
        }
        file.setLastModified(System.currentTimeMillis())
        return file
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                       selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        private val pending = ConcurrentHashMap.newKeySet<String>()
        private val prefetch = ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, ArrayBlockingQueue(8),
            { task -> Thread(task, "note-tile-prefetch").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } })
    }
}

internal object NoteTileStore {
    private val layouts = LruCache<String, RenderedNote>(3)
    private val imageKeys = LruCache<String, List<String>>(6)

    fun sourceKey(context: Context, key: String): String {
        val data = JSONObject(File(context.noBackupFilesDir, "note-layouts/$key.json").readText())
        val source = data.optString("source", key)
        if (!source.matches(Regex("[a-f0-9]{64}"))) throw FileNotFoundException()
        return source
    }

    fun prepare(context: Context, markdown: String, width: Int, background: Int,
                              text: Int, accent: Int, vaultAssets: String = ""): Pair<String, RenderedNote> {
        val density = context.resources.displayMetrics.densityDpi
        val fontScale = context.resources.configuration.fontScale
        val style = "uri-tiles-v14|$density|$fontScale|$width|$background|$text|$accent|${NoteTasks.fingerprint(vaultAssets)}"
        val key = NoteTasks.fingerprint("$style|$markdown")
        val directory = File(context.noBackupFilesDir, "note-layouts").apply { mkdirs() }
        val snapshot = File(directory, "$key.json")
        synchronized(this) {
            if (!snapshot.exists()) {
                val cache = AtomicFile(snapshot)
                val output = cache.startWrite()
                try {
                    output.write(JSONObject().put("markdown", markdown).put("width", width)
                        .put("background", background).put("text", text).put("accent", accent)
                        .put("density", density).put("fontScale", fontScale)
                        .put("vaultAssets", vaultAssets)
                        .toString().toByteArray(Charsets.UTF_8))
                    cache.finishWrite(output)
                } catch (error: Exception) {
                    cache.failWrite(output)
                    throw error
                }
            }
        }
        val note = load(context, key)
        if (imageKeys.get(key) == null) {
            val base = NoteTasks.fingerprint("$style|${note.height}|${note.tileHeight}|${note.uncheckedMarkdown(markdown)}")
            val tiles = (0 until note.tileCount).map { position ->
                val imageKey = NoteTasks.fingerprint("$base|$position|${note.checkboxStateForTile(position)}")
                val alias = File(directory, "$imageKey.json")
                synchronized(this) {
                    if (!alias.exists()) {
                        val cache = AtomicFile(alias)
                        val output = cache.startWrite()
                        try {
                            output.write(JSONObject().put("source", key).toString().toByteArray(Charsets.UTF_8))
                            cache.finishWrite(output)
                        } catch (error: Exception) {
                            cache.failWrite(output)
                            throw error
                        }
                    }
                }
                imageKey
            }
            imageKeys.put(key, tiles)
        }
        return key to note
    }

    fun load(context: Context, key: String): RenderedNote {
        layouts.get(key)?.let { return it }
        val snapshot = File(context.noBackupFilesDir, "note-layouts/$key.json")
        if (!snapshot.isFile) throw FileNotFoundException("Note snapshot unavailable")
        val data = JSONObject(snapshot.readText())
        if (data.has("source")) return load(context, data.getString("source"))
        return onMain {
            layouts.get(key)?.let { return@onMain it }
            val configuration = android.content.res.Configuration(context.resources.configuration).apply {
                densityDpi = data.optInt("density", densityDpi)
                fontScale = data.optDouble("fontScale", fontScale.toDouble()).toFloat()
            }
            BitmapRenderer(context.applicationContext.createConfigurationContext(configuration))
                .layoutNote(data.getString("markdown"), data.getInt("width"),
                data.getInt("background"), data.getInt("text"), data.getInt("accent"),
                tileByteLimit = 4 * 1024 * 1024, tileHeightDp = 512,
                vaultSnapshot = VaultSnapshot.parse(data.optString("vaultAssets")))
                .also { layouts.put(key, it) }
        }
    }

    fun uri(context: Context, key: String, position: Int): Uri = Uri.Builder()
        .scheme("content").authority("${context.packageName}.note-tiles")
        .appendPath(imageKeys.get(key)?.getOrNull(position) ?: key).appendPath(position.toString()).build()

    fun fingerprint(context: Context, key: String): String = NoteTasks.fingerprint(
        JSONObject(File(context.noBackupFilesDir, "note-layouts/$key.json").readText())
            .getString("markdown").trimEnd())
}
