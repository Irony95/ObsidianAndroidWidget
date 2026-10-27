package com.example.obsidianandroidwidgets

import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonVisitor
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.tasklist.TaskListDrawable
import io.noties.markwon.ext.tasklist.TaskListItem
import io.noties.markwon.ext.tasklist.TaskListSpan
import org.commonmark.node.Code
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Node
import org.commonmark.node.Text
import java.util.IdentityHashMap
import java.util.UUID

internal class TaskSourcePlugin(markdown: String, textColor: Int, backgroundColor: Int,
    private val interactive: Boolean = true) : AbstractMarkwonPlugin() {
    private val tasks = NoteTasks.find(markdown)
    private val prefix = "\uE002${UUID.randomUUID()}:"
    private val token = Regex(Regex.escape(prefix) + "(\\d+)\uE003 ")
    private val sources = IdentityHashMap<TaskListItem, NoteTask>()
    private val drawable = TaskListDrawable(textColor, textColor, backgroundColor)

    override fun processMarkdown(markdown: String): String {
        val lines = markdown.split('\n').toMutableList()
        tasks.forEachIndexed { index, task ->
            if (lines[task.line].length <= task.markerOffset + 1 || lines[task.line].getOrNull(task.markerOffset - 1) != '[') return@forEachIndexed
            if (task.checked) lines[task.line] = lines[task.line].replaceRange(task.markerOffset, task.markerOffset + 1, "x")
            var offset = task.markerOffset + 2
            while (offset < lines[task.line].length && lines[task.line][offset].isWhitespace()) offset++
            lines[task.line] = lines[task.line].let {
                it.substring(0, offset) + prefix + index + "\uE003 " + it.substring(offset)
            }
        }
        return lines.joinToString("\n")
    }

    override fun beforeRender(node: Node) {
        fun clean(value: String) = token.replace(value, "")
        fun visit(current: Node) {
            if (current is TaskListItem) {
                val firstText = current.firstChild?.firstChild as? Text
                val match = firstText?.literal?.let { token.find(it) }
                if (match?.range?.first == 0) {
                    tasks.getOrNull(match.groupValues[1].toInt())?.let { sources[current] = it }
                }
            }
            when (current) {
                is Text -> current.literal = clean(current.literal)
                is Code -> current.literal = clean(current.literal)
                is FencedCodeBlock -> current.literal = clean(current.literal)
                is IndentedCodeBlock -> current.literal = clean(current.literal)
                is HtmlBlock -> current.literal = clean(current.literal)
                is HtmlInline -> current.literal = clean(current.literal)
            }
            var child = current.firstChild
            while (child != null) {
                visit(child)
                child = child.next
            }
        }
        visit(node)
    }

    override fun configureVisitor(builder: MarkwonVisitor.Builder) {
        builder.on(TaskListItem::class.java) { visitor, item ->
            val start = visitor.length()
            visitor.visitChildren(item)
            visitor.setSpans(start, SourceTaskSpan(visitor.configuration().theme(), drawable, item.isDone,
                sources[item]?.takeIf { interactive }, sources[item]?.completed ?: item.isDone))
            if (visitor.hasNext(item)) visitor.ensureNewLine()
        }
    }
}

internal class SourceTaskSpan(theme: MarkwonTheme, drawable: TaskListDrawable, done: Boolean,
                              val source: NoteTask?, val completed: Boolean = source?.completed ?: done) : TaskListSpan(theme, drawable, done)
