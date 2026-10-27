package com.example.obsidianandroidwidgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager

internal object NovaWidgetSizing {
    suspend fun apply(context: Context, id: GlanceId, configuredHost: String?) {
        if (Build.VERSION.SDK_INT < 31) return
        val host = context.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName
        if (host != "com.teslacoilsw.launcher" && configuredHost != "com.teslacoilsw.launcher") return
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val manager = AppWidgetManager.getInstance(context)
        val options = manager.getAppWidgetOptions(widgetId)
        val sizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES).orEmpty()
        if (sizes.isEmpty() || sizes.all { it.height == 1f }) return
        val widths = sizes.map { it.width }.distinct().filter { it > 0 }
        manager.updateAppWidgetOptions(widgetId, Bundle().apply {
            putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, ArrayList(widths.map { SizeF(it, 1f) }))
        })
    }
}
