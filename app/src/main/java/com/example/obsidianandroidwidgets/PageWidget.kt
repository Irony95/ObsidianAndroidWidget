package com.example.obsidianandroidwidgets;

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.background
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.work.WorkManager
import java.io.FileReader
import java.net.URLEncoder

object PageWidget: GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    fun Context.toPx(dp: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp,
        resources.displayMetrics)

    val buttonSize = 40

    val mdFilePathKey = stringPreferencesKey("mdFilePathKey")
    val vaultPathKey = stringPreferencesKey("vaultPathKey")
    val textKey = stringPreferencesKey("textKey")
    val showTools = booleanPreferencesKey("showTools")

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        super.onDelete(context, glanceId)
        val widgetCount = GlanceAppWidgetManager(context).getGlanceIds(PageWidget.javaClass).size
        if (widgetCount == 0)
        {
            //cancel once all the widgets are deleted
            WorkManager
                .getInstance(context)
                .cancelUniqueWork(WORK_NAME)
        }
    }
    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val mdFilePath = currentState(key=mdFilePathKey) ?: ""
            val text = currentState(key=textKey) ?: getNoteText(mdFilePath)
            val vaultPath = currentState(key= vaultPathKey) ?: ""
            val showTools = currentState(key=showTools) ?: true

            val renderer = BitmapRenderer(context, "$vaultPath/.obsidian/appearance.json")

            var dpWidth = if (showTools) LocalSize.current.width.value - buttonSize else LocalSize.current.width.value
            val localWidth = context.toPx(dpWidth)

            var encodedPath = Environment.getExternalStorageDirectory().toString() + "/" + mdFilePath
            encodedPath = URLEncoder.encode(encodedPath, "UTF-8").dropLast(3)
            val openNote: Intent = Intent(Intent.ACTION_VIEW,
                Uri.parse("obsidian://open?path=$encodedPath")
            )

            var encodedVault = URLEncoder.encode(vaultPath, "UTF-8")
            val newNote: Intent = Uri.parse("obsidian://new?vault=$encodedVault&name=new%20note").let { webpage ->
                Intent(Intent.ACTION_VIEW, webpage)
            }


            Row(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(10.dp)
                    .padding(5.dp)
                    .background(Color.DarkGray),
            ) {
                LazyColumn(
                    modifier = GlanceModifier
                        .defaultWeight()
                ) {
                    item {
                        val remoteView = RemoteViews(LocalContext.current.packageName, R.layout.test_layout)

                        remoteView.setImageViewBitmap(R.id.imageView,renderer.renderBitmap(text, localWidth.toInt()))
                        AndroidRemoteViews(remoteView, modifier = GlanceModifier.clickable(actionStartActivity(openNote)).fillMaxSize())
                    }
                }
                if (showTools) {
                        Column{
                            Image(
                                provider = ImageProvider(R.drawable.baseline_refresh_24),
                                colorFilter = ColorFilter.tint(GlanceTheme.colors.inversePrimary),
                                contentDescription = "refresh",
                                modifier = GlanceModifier
                                    .clickable(actionRunCallback<ReloadWidget>())
                                    .size(buttonSize.dp)
                            )

                            Image(
                                provider = ImageProvider(R.drawable.baseline_add_circle_outline_24),
                                colorFilter = ColorFilter.tint(GlanceTheme.colors.inversePrimary),
                                contentDescription = "add",
                                modifier = GlanceModifier
                                    .clickable(actionStartActivity(newNote))
                                    .size(buttonSize.dp)
                            )
                        }
                }
            }
        }
    }
    fun getNoteText(directory : String): String {
        if (directory == "") return ""
        val reader = FileReader(Environment.getExternalStorageDirectory().toString() + "/" + directory)
        val text = reader.readText()
        reader.close()
        return text
    }
}

class SimplePageWidgetReceiver: GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget
        get() = PageWidget
}


class ReloadWidget: ActionCallback {


    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {

        updateAppWidgetState(context, glanceId) { prefs ->
            val text = PageWidget.getNoteText(prefs[PageWidget.mdFilePathKey] ?: "")
            prefs[PageWidget.textKey] = text
            Log.d("test", "updated the text")
        }
        PageWidget.update(context, glanceId)
    }
}