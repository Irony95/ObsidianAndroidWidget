package com.example.obsidianandroidwidgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NoteActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                val id = GlanceAppWidgetManager(this@NoteActionActivity).getGlanceIdBy(widgetId)
                val prefs = getAppWidgetState(this@NoteActionActivity, PreferencesGlanceStateDefinition, id)
                when (intent.getStringExtra("kind")) {
                    "copy" -> {
                        val key = intent.getStringExtra("noteKey").orEmpty()
                        require(key.matches(Regex("[a-f0-9]{64}")))
                        val code = NoteTileStore.load(this@NoteActionActivity, key).code(intent.getIntExtra("copyIndex", -1))
                        getSystemService(android.content.ClipboardManager::class.java)
                            .setPrimaryClip(android.content.ClipData.newPlainText("Code", code))
                        if (android.os.Build.VERSION.SDK_INT < 33) Toast.makeText(this@NoteActionActivity, "Code copied", Toast.LENGTH_SHORT).show()
                    }
                    "task" -> NoteTaskAction.toggle(this@NoteActionActivity, id,
                        intent.getIntExtra("line", -1), intent.getStringExtra("originalLine").orEmpty(),
                        intent.getStringExtra("fingerprint").orEmpty())
                    else -> {
                        val vault = prefs[PageWidget.vaultNameKey]?.takeIf { it.isNotBlank() }
                            ?: PageWidget.resolveVaultName(this@NoteActionActivity, prefs[PageWidget.vaultUriKey].orEmpty())
                        val file = prefs[PageWidget.obsidianFilePathKey]?.takeIf { it.isNotBlank() }
                            ?: PageWidget.deriveObsidianFilePath(prefs[PageWidget.noteUriKey].orEmpty(),
                                prefs[PageWidget.vaultUriKey].orEmpty(), prefs[PageWidget.noteTitleKey].orEmpty())
                        val destination = if (intent.getStringExtra("kind") == "link") {
                            NoteLinks.resolve(intent.getStringExtra("url").orEmpty(), vault, file)
                        } else PageWidget.noteOpenUri(vault, file)
                        startActivity(Intent(Intent.ACTION_VIEW, destination).apply {
                            if (destination.scheme == "obsidian") {
                                setPackage("md.obsidian")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                        })
                    }
                }
            } catch (error: Exception) {
                android.util.Log.e("NoteAction", "Note action failed", error)
                Toast.makeText(this@NoteActionActivity,
                    if (error is SecurityException) "Reconfigure this widget to grant note editing access."
                    else "Unable to complete action. Open the note in Obsidian or try refreshing.",
                    Toast.LENGTH_LONG).show()
            } finally {
                finish()
            }
        }
    }
}
