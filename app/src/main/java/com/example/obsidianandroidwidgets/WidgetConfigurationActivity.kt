package com.example.obsidianandroidwidgets

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.obsidianandroidwidgets.ui.theme.ObsidianAndroidWidgetsTheme
import com.vladsch.flexmark.ext.emoji.EmojiExtension
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import com.vladsch.flexmark.util.misc.Extension
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import kotlin.io.path.Path


class WidgetConfigurationActivity : ComponentActivity() {

    private val INITIAL_URI = Environment.getExternalStorageDirectory().absolutePath

    companion object {
        const val MD_PAGE_PATH_KEY = "markdown_page_path"
        const val FOLLOW_THEME_CONFIG_KEY = "follow_vault_theme_setting"
        const val RIBBON_BUTTONS_CONFIG_KEY = "show_ribbon_buttons"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        WebView.enableSlowWholeDocumentDraw()
        if (Build.VERSION.SDK_INT >= 30) {
            if (!Environment.isExternalStorageManager()) {
                val getpermission = Intent()
                getpermission.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                startActivity(getpermission)
            }
        }

        val sharedPref = baseContext.getSharedPreferences("obsidian_widget_configs", Context.MODE_PRIVATE)
        super.onCreate(savedInstanceState)
        var fileMutable = MutableStateFlow(sharedPref.getString(MD_PAGE_PATH_KEY, ""))

        val getContent =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (it.resultCode == Activity.RESULT_OK)
                {
                    fileMutable.value = (it.data?.data?.lastPathSegment)?.removeRange(0, 8)
                    Log.d("test", fileMutable.value ?: "")
                }
            }


        setContent {
            val context = LocalContext.current

            val filePath by fileMutable.collectAsState()

            var followTheme by remember {
                mutableStateOf(sharedPref.getBoolean(FOLLOW_THEME_CONFIG_KEY, true))
            }
            var showRibbon by remember {
                mutableStateOf(sharedPref.getBoolean(RIBBON_BUTTONS_CONFIG_KEY, false))
            }

            var imageBmp by remember {
                mutableStateOf<Bitmap?>(null)
            }
            val md = """                          
                - [ ] This should work?
                - [x] What about this?
    
                # This is a h1 title
                ## This is a h2 Title
                ```
                This is a codeblock
                ```
                *Bold!!!*
                
                more m
                asdf
                as
            """.trimIndent()
            val render = BitmapRenderer(context)
            render.setMarkdown(md, 1000) {
                imageBmp = it
            }


            ObsidianAndroidWidgetsTheme {
                val appWidgetId = intent?.extras?.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    filePicker(filePath = filePath ?: "") {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "text/markdown"
                            putExtra(DocumentsContract.EXTRA_INITIAL_URI, INITIAL_URI)
                        }
                        getContent.launch(intent)
                    }
                    checkboxAndText(followTheme, "Follow Vault Theme") {
                        followTheme = it
                    }

                    checkboxAndText(showRibbon, "Show Obsidian ribbon buttons") {
                        showRibbon = it
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.End,

                    ) {

                        Button(onClick = {
                            val editor = sharedPref.edit()
                            editor.putString(MD_PAGE_PATH_KEY, filePath ?: "")
                            editor.putBoolean(FOLLOW_THEME_CONFIG_KEY, followTheme)
                            editor.putBoolean(RIBBON_BUTTONS_CONFIG_KEY, showRibbon)
                            editor.commit()

                            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            setResult(Activity.RESULT_OK, resultValue)
                            finish()
                        }) {
                            Text("Complete")
                        }
                    }
                    Log.d("test", "rendered on jpc")
                    if (imageBmp != null)
                    {
                        Log.d("test", "asfdasf")
                        LazyColumn(content = {
                            item {
                                Image(modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Red),
                                    bitmap = imageBmp!!.asImageBitmap(),
                                    contentDescription = "asdfsadf"
                                )
                            }
                        })

                    }
                }
            }
        }
    }
}

@Composable
fun filePicker(filePath: String, onClick: () -> Unit) {
    val scroll = rememberScrollState(0)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    )
    {
        Text(filePath, modifier = Modifier
            .width(250.dp)
            .horizontalScroll(scroll))
        Button(onClick = onClick, modifier = Modifier.padding(5.dp)) {
            Text(text = "Select Page")
        }
    }
}

@Composable
fun checkboxAndText(checked: Boolean, text: String, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)

        Text(text = text, modifier = Modifier.clickable { onCheckedChange(!checked) })
    }
}
