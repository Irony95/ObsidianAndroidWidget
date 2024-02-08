package com.example.obsidianandroidwidgets;

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.color.ColorProviders
import androidx.glance.currentState
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.work.WorkManager
import java.io.BufferedReader
import java.io.FileNotFoundException
import java.io.FileReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URLEncoder

object PageWidget: GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override val stateDefinition = PreferencesGlanceStateDefinition
    fun Context.toPx(dp: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp,
        resources.displayMetrics)

    val buttonSize = 50
    val paddingSize = 7

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
            val text = currentState(key=textKey) ?: getNoteText(context, mdFilePath)
            val vaultPath = currentState(key= vaultPathKey) ?: ""
            val showTools = currentState(key=showTools) ?: true

            val renderer = BitmapRenderer(context)

            var dpWidth = if (showTools)
                LocalSize.current.width.value - buttonSize - paddingSize*2
            else
                LocalSize.current.width.value - paddingSize*2
            val localWidth = context.toPx(dpWidth)

            var encodedFile = mdFilePath.split("/").lastOrNull()?.dropLast(3)
            encodedFile = URLEncoder.encode(encodedFile, "UTF-8").replace("+", "%20")
            var encodedVault = vaultPath.split("/").lastOrNull()
            encodedVault = URLEncoder.encode(encodedVault, "UTF-8").replace("+", "%20")

            val openNote = Intent(Intent.ACTION_VIEW,
                Uri.parse("obsidian://open?vault=$encodedVault&file=$encodedFile")
            )

            val newNote = Intent(Intent.ACTION_VIEW,
                Uri.parse("obsidian://new?vault=$encodedVault&name=New%20note")
            )



            Row(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(10.dp)
                    .padding(paddingSize.dp)
                    .background(Color(0xff262626)),
            ) {
                LazyColumn(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .background(Color(0xff1e1e1e))
                        .cornerRadius(10.dp)
                ) {
                    item {
                        val fileName = mdFilePath.split("/").lastOrNull()
                        if (fileName != null)
                        {
                            Text(
                                text = fileName.dropLast(3),
                                modifier = GlanceModifier
                                    .fillMaxSize()
                                    .padding(bottom = (paddingSize*3).dp)
                                    .clickable(actionStartActivity(openNote)),
                                style = TextStyle(
                                    fontSize = 32.sp,
                                    color = ColorProvider(R.color.white),
                                    fontWeight = FontWeight.Bold,
                                    fontStyle = FontStyle.Italic
                                )
                            )
                        }
                    }
                    item {

                        val remoteView = RemoteViews(LocalContext.current.packageName, R.layout.test_layout)
                        remoteView.setImageViewBitmap(R.id.imageView,renderer.renderBitmap(text, localWidth.toInt()))
                        AndroidRemoteViews(
                            remoteView,
                            modifier = GlanceModifier
                                .clickable(actionStartActivity(openNote))
                                .fillMaxSize()
                        )
                    }
                }
                if (showTools) {
                        Column(modifier = GlanceModifier.padding(start = paddingSize.dp)){
                            Image(
                                provider = ImageProvider(R.drawable.baseline_refresh_24),
                                colorFilter = ColorFilter.tint(ColorProvider(R.color.button_color)),
                                contentDescription = "refresh",
                                modifier = GlanceModifier
                                    .clickable(actionRunCallback<ReloadWidget>())
                                    .size(buttonSize.dp)
                                    .padding(top=paddingSize.dp)
                            )

                            Image(
                                provider = ImageProvider(R.drawable.baseline_add_circle_outline_24),
                                colorFilter = ColorFilter.tint(ColorProvider(R.color.button_color)),
                                contentDescription = "add",
                                modifier = GlanceModifier
                                    .clickable(actionStartActivity(newNote))
                                    .size(buttonSize.dp)
                                    .padding(top=paddingSize.dp)
                            )
                        }
                }
            }
        }
    }

    private fun loadMarkdown(context: Context, uri: Uri): String {
        val contentResolver = context.contentResolver

        val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        // Check for the freshest data.
        contentResolver.takePersistableUriPermission(uri, takeFlags)

        try {
            val ins: InputStream = contentResolver.openInputStream(uri)!!
            val reader = BufferedReader(InputStreamReader(ins, "utf-8"))
            val data = reader.lines().reduce { s, t -> s + "\n" + t }
            return data.get()
        } catch (err: FileNotFoundException) {
            return ""
        }
    }
    fun getNoteText(context: Context, directory : String): String {
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
            val text = PageWidget.getNoteText(context,prefs[PageWidget.mdFilePathKey] ?: "")
            prefs[PageWidget.textKey] = text
        }
        PageWidget.update(context, glanceId)
    }
}