package com.example.obsidianandroidwidgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.widget.TextView
import androidx.compose.ui.unit.sp
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import org.json.JSONObject
import java.io.File
import java.io.FileReader


class BitmapRenderer(val context: Context, val appearancePath: String) {

    var markwon: Markwon

    init {
        var builder = Markwon.builder(context)
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(TablePlugin.create(context))
            .usePlugin(SoftBreakAddsNewLinePlugin.create())
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(HtmlPlugin.create())
            .usePlugin(object:  AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder
                }
            })
        markwon = builder.build()
    }
    fun loadTheme(builder: Markwon.Builder)
    {
        val reader = FileReader(appearancePath)
        val appearanceName = JSONObject(reader.readText()).getString("cssTheme")
        reader.close()

        val themeFilePath = File(appearancePath).parent + "/themes/" + appearanceName + "/theme.css"
        val reader2 = FileReader(themeFilePath)
        val css = reader2.readText()
        reader2.close()
    }
    fun renderBitmap(string: String, width : Int) : Bitmap {
        var textView = TextView(context)
        textView.setTextColor(Color.WHITE)
        textView.textSize = 18F
        textView.width = width
        markwon.setMarkdown(textView, string)
//        textView.text = string
        textView.measure(0,0)
        textView.layout(0,0,width, textView.measuredHeight)

        val bitmap = Bitmap.createBitmap(width, textView.measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.DKGRAY)
        canvas.translate(0F, 0F)
        textView.draw(canvas)

        return bitmap
   }

}