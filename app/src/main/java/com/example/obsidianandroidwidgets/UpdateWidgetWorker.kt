package com.example.obsidianandroidwidgets

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

const val WORK_NAME = "update-markdown-widgets"
class UpdateWidgetWorker(private val context: Context,
                         workerParams: WorkerParameters,) : Worker(context, workerParams) {
    override fun doWork(): Result {
        CoroutineScope(Dispatchers.IO).launch {
            GlanceAppWidgetManager(context).getGlanceIds(PageWidget.javaClass).forEach {
                updateAppWidgetState(context, it) { prefs ->
                    val text = PageWidget.getNoteText(context,prefs[PageWidget.mdFilePathKey] ?: "")
                    prefs[PageWidget.textKey] = text
                    Log.d("test", "updated the text in worker!")
                }
                PageWidget.update(context, it)
            }

        }

        return Result.success()
    }
}