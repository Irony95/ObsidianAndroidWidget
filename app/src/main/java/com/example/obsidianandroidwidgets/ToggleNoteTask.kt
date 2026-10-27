package com.example.obsidianandroidwidgets

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ToggleNoteTask : ActionCallback {
    companion object {
        val line = ActionParameters.Key<Int>("taskLine")
        val originalLine = ActionParameters.Key<String>("taskOriginalLine")
        val fingerprint = ActionParameters.Key<String>("taskFingerprint")
    }

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        try {
            NoteTaskAction.toggle(context, glanceId, parameters[line] ?: return,
                parameters[originalLine] ?: return, parameters[fingerprint] ?: return)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.e("NoteAction", "Checkbox edit failed", error)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Unable to edit the task. Refresh or reconfigure note access.", Toast.LENGTH_LONG).show()
            }
        }
    }
}

internal object NoteTaskAction {
    private val edits = Mutex()

    suspend fun toggle(context: Context, id: GlanceId, line: Int, originalLine: String, fingerprint: String) = edits.withLock {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val uri = prefs[PageWidget.noteUriKey]?.let(Uri::parse) ?: return@withLock
        val saved = withContext(Dispatchers.IO) {
            NoteTaskEditor.toggle(context, uri, fingerprint, line, originalLine)
        }
        if (saved == null) withContext(Dispatchers.Main) {
            Toast.makeText(context, "Note changed since it loaded. Refreshed; please tap again.", Toast.LENGTH_LONG).show()
        }
        val text = saved ?: withContext(Dispatchers.IO) { PageWidget.readNoteText(context, uri.toString()) }
        GlanceAppWidgetManager(context).getGlanceIds(PageWidget::class.java).forEach { widget ->
            val state = getAppWidgetState(context, PreferencesGlanceStateDefinition, widget)
            if (state[PageWidget.noteUriKey] == uri.toString()) {
                updateAppWidgetState(context, widget) { it[PageWidget.textKey] = text }
                PageWidget.update(context, widget)
            }
        }
    }
}
