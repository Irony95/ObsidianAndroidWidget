package com.example.obsidianandroidwidgets

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import io.noties.markwon.syntax.Prism4jTheme
import io.noties.prism4j.Prism4j

internal class AtomCodeTheme(private val dark: Boolean) : Prism4jTheme {
    override fun background() = if (dark) 0xFF282C34.toInt() else 0xFFFAFAFA.toInt()
    override fun textColor() = if (dark) 0xFFABB2BF.toInt() else 0xFF383A42.toInt()

    override fun apply(language: String, syntax: Prism4j.Syntax, builder: SpannableStringBuilder, start: Int, end: Int) {
        val type = syntax.alias() ?: syntax.type()
        val colour = when (type) {
            "comment", "prolog", "doctype", "cdata" -> if (dark) 0xFF5C6370 else 0xFFA0A1A7
            "keyword", "atrule", "selector" -> if (dark) 0xFFC678DD else 0xFFA626A4
            "string", "char", "attr-value", "regex", "inserted" -> if (dark) 0xFF98C379 else 0xFF50A14F
            "number", "boolean", "constant" -> if (dark) 0xFFD19A66 else 0xFF986801
            "function", "function-variable" -> if (dark) 0xFF61AFEF else 0xFF4078F2
            "class-name", "builtin" -> if (dark) 0xFFE5C07B else 0xFFC18401
            "property", "tag", "deleted", "symbol", "variable" -> if (dark) 0xFFE06C75 else 0xFFE45649
            "operator", "url" -> if (dark) 0xFF56B6C2 else 0xFF0184BC
            else -> return
        }
        builder.setSpan(ForegroundColorSpan(colour.toInt()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}
