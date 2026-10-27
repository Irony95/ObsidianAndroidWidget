package com.example.obsidianandroidwidgets

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import org.json.JSONObject

internal class ToggleCallout : ActionCallback {
    companion object {
        val idKey = ActionParameters.Key<String>("calloutId")
        val closedKey = ActionParameters.Key<Boolean>("calloutClosed")
    }
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[idKey] ?: return
        updateAppWidgetState(context, glanceId) {
            val overrides = JSONObject(it[PageWidget.calloutsKey] ?: "{}")
            overrides.put(id, parameters[closedKey] ?: false)
            it[PageWidget.calloutsKey] = overrides.toString()
        }
        PageWidget.update(context, glanceId)
    }
}
