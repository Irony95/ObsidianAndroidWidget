package com.example.obsidianandroidwidgets

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

internal object WidgetRefreshSchedule {
    val options = listOf(15L, 30L, 60L, 180L, 360L, 1440L, 0L)
    fun label(minutes: Long): String = when (minutes) {
        0L -> "Manual only"
        15L, 30L -> "$minutes minutes"
        60L -> "1 hour"
        1440L -> "24 hours"
        else -> "${minutes / 60} hours"
    }

    fun isDue(minutes: Long, lastUpdated: Long, now: Long, force: Boolean = false): Boolean =
        force || (minutes >= 15 && (now < lastUpdated || now - lastUpdated >= minutes * 60000))

    suspend fun reschedule(context: Context) {
        val intervals = GlanceAppWidgetManager(context).getGlanceIds(PageWidget::class.java).map { id ->
            getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[PageWidget.updateMinutesKey] ?: 15L
        }.filter { it >= 15L }
        val manager = WorkManager.getInstance(context)
        val minimum = intervals.minOrNull()
        if (minimum == null) manager.cancelUniqueWork(WORK_NAME)
        else manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<UpdateWidgetWorker>(minimum, TimeUnit.MINUTES).build())
    }
}
