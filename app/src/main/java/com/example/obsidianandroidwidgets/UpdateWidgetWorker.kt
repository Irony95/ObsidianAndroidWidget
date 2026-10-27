package com.example.obsidianandroidwidgets

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

const val WORK_NAME = "update-markdown-widgets"

class UpdateWidgetWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        GlanceAppWidgetManager(applicationContext).getGlanceIds(PageWidget::class.java).forEach {
            val prefs = getAppWidgetState(applicationContext, PreferencesGlanceStateDefinition, it)
            val minutes = prefs[PageWidget.updateMinutesKey] ?: 15L
            val force = inputData.getBoolean("force", false)
            if (WidgetRefreshSchedule.isDue(minutes, prefs[PageWidget.lastUpdatedKey] ?: 0L, System.currentTimeMillis(), force)) {
                val changed = refreshNoteSnapshot(applicationContext, it)
                if (changed || force) PageWidget.update(applicationContext, it)
            }
        }
        WidgetRefreshSchedule.reschedule(applicationContext)
        return Result.success()
    }
}
