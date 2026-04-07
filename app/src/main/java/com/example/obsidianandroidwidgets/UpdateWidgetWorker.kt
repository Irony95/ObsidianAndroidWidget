package com.example.obsidianandroidwidgets

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

const val WORK_NAME = "update-markdown-widgets"

class UpdateWidgetWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        GlanceAppWidgetManager(applicationContext).getGlanceIds(PageWidget::class.java).forEach {
            updateAppWidgetState(applicationContext, it) { prefs ->
                prefs[PageWidget.textKey] = PageWidget.readNoteText(
                    applicationContext,
                    prefs[PageWidget.noteUriKey].orEmpty()
                )
            }
            PageWidget.update(applicationContext, it)
        }

        return Result.success()
    }
}
