package com.example.obsidianandroidwidgets

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Os
import java.io.File

internal object NoteTaskEditor {
    fun toggle(context: Context, uri: Uri, fingerprint: String, line: Int, originalLine: String): String? {
        context.contentResolver.openFileDescriptor(uri, "rw")?.use { descriptor ->
            fun read(): String {
                Os.lseek(descriptor.fileDescriptor, 0, android.system.OsConstants.SEEK_SET)
                return ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(descriptor.fileDescriptor))
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
            val original = read()
            if (NoteTasks.fingerprint(original.trimEnd()) != fingerprint) return null
            val updated = NoteTasks.toggle(original, line, originalLine) ?: return null
            val changedAt = original.indices.first { original[it] != updated[it] }
            val byteOffset = original.substring(0, changedAt).toByteArray(Charsets.UTF_8).size.toLong()
            File(context.filesDir, "last-task-edit-backup.md").writeText(original, Charsets.UTF_8)
            if (read() != original) return null
            check(Os.pwrite(descriptor.fileDescriptor, byteArrayOf(updated[changedAt].code.toByte()), 0, 1, byteOffset) == 1)
            return read()
        }
        error("Cannot open this document for editing")
    }
}
