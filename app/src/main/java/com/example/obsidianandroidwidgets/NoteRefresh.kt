package com.example.obsidianandroidwidgets

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun refreshNoteSnapshot(context: Context, id: GlanceId): Boolean = withContext(Dispatchers.IO) {
    val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
    val text = PageWidget.readNoteText(context, prefs[PageWidget.noteUriKey].orEmpty())
    val assets = VaultSnapshot.capture(context, prefs[PageWidget.vaultUriKey].orEmpty(),
        prefs[PageWidget.vaultNameKey].orEmpty(), prefs[PageWidget.obsidianFilePathKey].orEmpty(), text)
    val previousAccent = ObsidianTheme.cachedAccentColor(context, prefs[PageWidget.vaultUriKey], ObsidianTheme.DEFAULT_ACCENT_COLOR)
    val accent = if (prefs[PageWidget.accentModeKey] != "custom") {
        ObsidianTheme.readAccentColor(context, prefs[PageWidget.vaultUriKey], ObsidianTheme.DEFAULT_ACCENT_COLOR)
    } else previousAccent
    updateAppWidgetState(context, id) {
        if (text != prefs[PageWidget.textKey]) it.remove(PageWidget.calloutsKey)
        it[PageWidget.textKey] = text
        it[PageWidget.vaultAssetsKey] = assets
        it[PageWidget.lastUpdatedKey] = System.currentTimeMillis()
    }
    text != prefs[PageWidget.textKey] || assets != prefs[PageWidget.vaultAssetsKey] || accent != previousAccent
}
