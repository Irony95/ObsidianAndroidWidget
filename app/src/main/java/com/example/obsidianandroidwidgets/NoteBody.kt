package com.example.obsidianandroidwidgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size

@Composable
internal fun NoteBody(context: Context, widgetId: Int, prepared: Pair<String, RenderedNote>,
                      markdown: String, modifier: GlanceModifier, configuredHost: String? = null) {
    val key = prepared.first
    val note = prepared.second
    val density = context.resources.displayMetrics.density
    val fingerprint = remember(markdown) { NoteTasks.fingerprint(markdown.trimEnd()) }
    val hosts = noteImageHosts(context, configuredHost)
    val images = Uri.parse("content://${context.packageName}.note-tiles/")
    hosts.forEach { context.grantUriPermission(it, images,
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION) }
    LazyColumn(modifier = modifier) {
        for (position in 0 until note.tileCount) {
            item(itemId = position.toLong()) {
                val uri = remember(key, position) { NoteTileStore.uri(context, key, position) }
                val row = remember(uri) {
                    RemoteViews(context.packageName, R.layout.widget_note_tile).apply {
                        setInt(R.id.noteTile, "setBackgroundColor", note.backgroundColor)
                        setImageViewUri(R.id.noteTileImage, uri)
                    }
                }
                val targets = remember(key, position) { note.targetsForTile(position) }
                val pixels = minOf(note.tileHeight, note.height - position * note.tileHeight)
                Box(GlanceModifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp,
                    top = if (position == 0) 4.dp else 0.dp,
                    bottom = if (position == note.tileCount - 1) 4.dp else 0.dp)) {
                    Box(GlanceModifier.fillMaxWidth().height((pixels / density).dp)) {
                        AndroidRemoteViews(row, modifier = GlanceModifier.fillMaxSize().clickable(
                            actionStartActivity(noteIntent(context, widgetId, key, position, null, fingerprint))))
                        NoteTargets(context, widgetId, key, position, targets, fingerprint, density)
                    }
                }
            }
        }
    }
}

internal fun noteImageHosts(context: Context, configuredHost: String?): Set<String> {
    val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val manager = context.packageManager
    val current = manager.resolveActivity(home, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        ?.activityInfo?.packageName?.takeUnless { it == "android" }
    val configured = configuredHost?.takeIf { candidate ->
        manager.queryIntentActivities(home, 0).any { it.activityInfo.packageName == candidate }
    }
    return listOfNotNull(current, configured).toSet()
}

@Composable
private fun NoteTargets(context: Context, widgetId: Int, key: String, position: Int,
                        targets: List<NoteHitTarget>, fingerprint: String, density: Float) {
    if (targets.size > 8) {
        targets.chunked((targets.size + 7) / 8).forEach { group ->
            Box(GlanceModifier.fillMaxSize()) {
                NoteTargets(context, widgetId, key, position, group, fingerprint, density)
            }
        }
    } else {
        targets.forEach { target ->
            val rect = target.bounds
            Box(GlanceModifier.padding(start = (rect.left / density).dp, top = (rect.top / density).dp)) {
                val action = target.task?.let { task ->
                    actionRunCallback<ToggleNoteTask>(actionParametersOf(
                        ToggleNoteTask.line to task.line,
                        ToggleNoteTask.originalLine to task.originalLine,
                        ToggleNoteTask.fingerprint to fingerprint))
                } ?: if (target.url?.startsWith("widget-callout://") == true) {
                    val uri = Uri.parse(target.url)
                    actionRunCallback<ToggleCallout>(actionParametersOf(
                        ToggleCallout.idKey to uri.getQueryParameter("id").orEmpty(),
                        ToggleCallout.closedKey to (uri.getQueryParameter("closed") == "true")))
                } else actionStartActivity(noteIntent(context, widgetId, key, position, target, fingerprint))
                Box(GlanceModifier.size((rect.width() / density).dp, (rect.height() / density).dp)
                    .clickable(action)) {}
            }
        }
    }
}

private fun noteIntent(context: Context, widgetId: Int, key: String, position: Int,
                       target: NoteHitTarget?, fingerprint: String): Intent {
    val kind = when { target?.copyIndex != null -> "copy"; target?.task != null -> "task"; target?.url != null -> "link"; else -> "open" }
    val discriminator = target?.copyIndex?.toString() ?: target?.task?.line?.toString() ?: target?.url.orEmpty()
    return Intent(context, NoteActionActivity::class.java).apply {
        data = Uri.Builder().scheme("widget-note-action").authority(widgetId.toString())
            .appendPath(key).appendPath(position.toString()).appendPath(kind)
            .appendQueryParameter("target", discriminator).build()
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        putExtra("kind", kind)
        target?.url?.let { putExtra("url", it) }
        target?.copyIndex?.let { putExtra("noteKey", key); putExtra("copyIndex", it) }
        target?.task?.let {
            putExtra("line", it.line)
            putExtra("originalLine", it.originalLine)
            putExtra("fingerprint", fingerprint)
        }
    }
}
