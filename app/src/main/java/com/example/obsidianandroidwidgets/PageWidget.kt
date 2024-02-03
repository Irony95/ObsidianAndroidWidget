package com.example.obsidianandroidwidgets;

import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
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
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tasklist.TaskListPlugin
import java.io.FileReader
import java.net.URLEncoder

object PageWidget: GlanceAppWidget() {
    fun Context.toPx(dp: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp,
        resources.displayMetrics)

    val textKey = stringPreferencesKey("text")
    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val renderer = BitmapRenderer(context)

        val sharedPreferences = context.getSharedPreferences("obsidian_widget_configs", Context.MODE_PRIVATE)
        val mdFilePath = sharedPreferences.getString(WidgetConfigurationActivity.MD_PAGE_PATH_KEY, "")
        val service = Intent(context, FileSystemObserverService::class.java)
        service.putExtra(FileSystemObserverService.PATH_EXTRA, mdFilePath)

        context.startService(service)

        provideContent {
            val text = currentState(key = textKey) ?: ""
            val packageName = LocalContext.current.packageName
            val localWidth = context.toPx(LocalSize.current.width.value)
            Log.d("test", packageName.toString())

//            val intent = context.packageManager.getLaunchIntentForPackage("md.obsidian")
//            intent?.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            var filePath = Environment.getExternalStorageDirectory().toString() + "/" + mdFilePath
            filePath = URLEncoder.encode(filePath, "UTF-8").dropLast(3)
            Log.d("test", filePath)
            val openNote: Intent = Uri.parse("obsidian://open?path=$filePath").let { webpage ->
                Intent(Intent.ACTION_VIEW, webpage)
            }


            Row(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(50.dp)
                    .padding(5.dp)
                    .background(Color.LightGray),
            ) {
                LazyColumn(
                    modifier = GlanceModifier
                        .defaultWeight()
                ) {
                    item {

                        val remoteView = RemoteViews(packageName, R.layout.test_layout)

                        remoteView.setImageViewBitmap(R.id.imageView,renderer.renderedBitmap(text))
                        AndroidRemoteViews(remoteView, modifier = GlanceModifier.clickable(actionStartActivity(openNote)).fillMaxSize().background(Color.Red))

                    }
                }

                Column{
                    Image(
                        provider = ImageProvider(R.drawable.baseline_refresh_24),
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                        contentDescription = "refresh",
                        modifier = GlanceModifier
                            .clickable(actionRunCallback(IncrementActionCallback::class.java))
                            .size(30.dp)
                    )
                }


            }
        }
    }
}

class SimplePageWidgetReceiver: GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget
        get() = PageWidget
}


class IncrementActionCallback: ActionCallback {
    private fun getNoteText(context: Context): String {
        var text = ""
        val sf = context.getSharedPreferences("obsidian_widget_configs", Context.MODE_PRIVATE)
        val filePath = sf.getString(WidgetConfigurationActivity.MD_PAGE_PATH_KEY, "")
        if (filePath != null)
        {
            val reader = FileReader(Environment.getExternalStorageDirectory().toString() + "/" + filePath)
            text = reader.readText()
            reader.close()
        }
        return text
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val text = getNoteText(context)

        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[PageWidget.textKey] = text
        }
        PageWidget.update(context, glanceId)
    }
}

