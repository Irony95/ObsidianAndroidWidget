package com.example.obsidianandroidwidgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import android.widget.TextView
import io.noties.markwon.Markwon
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import org.json.JSONObject
import java.io.File
import java.io.FileReader
import java.net.URL


class BitmapRenderer(val context: Context) {
    companion object {
        //small padding for the image
        const val IMAGE_PAD = 15
    }

    var markwon: Markwon = Markwon.builder(context)
        .usePlugin(TaskListPlugin.create(context))
        .usePlugin(TablePlugin.create(context))
        .usePlugin(SoftBreakAddsNewLinePlugin.create())
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(HtmlPlugin.create())
        .build()

    fun renderBitmap(string: String, width : Int) : Bitmap {
        val paddedWidth = width-IMAGE_PAD*2
        var textView = TextView(context)
        textView.setTextColor(Color.WHITE)
        textView.textSize = 18F
        textView.width = paddedWidth
        markwon.setMarkdown(textView, string)
//        textView.text = string
        textView.measure(0,0)
        textView.layout(0,0,paddedWidth, textView.measuredHeight)

        val bitmap = Bitmap.createBitmap(paddedWidth, textView.measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.translate(0F, 0F)
        textView.draw(canvas)

        return bitmap
   }

}