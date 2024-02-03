package com.example.obsidianandroidwidgets

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import com.vladsch.flexmark.ext.emoji.EmojiExtension
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import com.vladsch.flexmark.util.misc.Extension
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tasklist.TaskListPlugin

class BitmapRenderer(val context: Context) {

    private var markwon: Markwon = Markwon.builder(context)
        .usePlugin(TaskListPlugin.create(context))
        .build()

    fun renderBitmap(string: String, width : Int) : Bitmap {
        var textView = TextView(context)
        markwon.setMarkdown(textView, string)

        
   }

}