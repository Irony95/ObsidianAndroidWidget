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
import com.vladsch.flexmark.ext.emoji.EmojiExtension
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import com.vladsch.flexmark.util.misc.Extension

class BitmapRenderer(val context: Context) {
    var renderedBitmap: Bitmap? = null
    val flexmarkOptions = MutableDataSet().set(
        Parser.EXTENSIONS, arrayListOf(
            TaskListExtension.create(),
            EmojiExtension.create()
        ) as Collection<Extension>
    )
    var parser:Parser
    var renderer: HtmlRenderer

    init {
        flexmarkOptions.set(HtmlRenderer.SOFT_BREAK, "<br />\n")
        parser = Parser.builder(flexmarkOptions).build()
        renderer = HtmlRenderer.builder(flexmarkOptions).build()
    }

    public fun setMarkdown(string: String, bmpWidth: Int, callback: (Bitmap) -> Unit = {}) {
        renderedBitmap = null

        val node = parser.parse(string)
        val html = renderer.render(node)
        val webView = WebView(context)

        webView.webViewClient = object: WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                webView.measure(0, 0)
                Log.d("test", webView.measuredHeight.toString())
                if (webView.measuredHeight == 0) { return }
                val bmp = Bitmap.createBitmap(bmpWidth, webView.measuredHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.translate(0F, 0F)
                webView.draw(canvas)
                //set the rendered bitmap once its rendered
                renderedBitmap = bmp
                callback(bmp)
            }
        }
        Log.d("test", bmpWidth.toString())
        webView.layout(0,0,bmpWidth, 1)
        webView.loadData(html, "text/html", "UTF-8")
    }

}