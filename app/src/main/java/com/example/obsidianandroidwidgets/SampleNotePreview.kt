package com.example.obsidianandroidwidgets

import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
internal fun SampleNotePreview(background: Color, text: Color, accent: Int, vaultName: String = "") {
    val context = LocalContext.current
    val density = LocalDensity.current
    val source = remember(context) { SampleNote.markdown(context) }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(4.dp)) {
        val width = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val note = remember(source, width, background, text, accent, vaultName) {
            BitmapRenderer(context).layoutNote(source, width, background.toArgb(), text.toArgb(), accent, interactive = false,
                vaultSnapshot = VaultSnapshot(org.json.JSONObject().put("vault", vaultName)))
        }
        val bitmap = remember(note) { note.drawSection(0, note.height) }
        DisposableEffect(bitmap) { onDispose { bitmap.recycle() } }
        val links = remember(note) {
            (0 until note.tileCount).flatMap { position ->
                note.targetsForTile(position).filter { it.url != null || it.copyIndex != null }.map { target ->
                    Rect(target.bounds).apply { offset(0, position * note.tileHeight) } to target
                }
            }
        }
        Image(bitmap.asImageBitmap(), "Sample markdown with an embedded photo and linked image credit",
            modifier = Modifier.fillMaxWidth().pointerInput(note) {
                detectTapGestures { point ->
                    val scale = note.width.toFloat() / size.width
                    links.firstOrNull { (rect, _) -> rect.contains((point.x * scale).toInt(), (point.y * scale).toInt()) }
                        ?.second?.let { target ->
                            if (target.copyIndex != null) {
                                context.getSystemService(android.content.ClipboardManager::class.java)
                                    .setPrimaryClip(android.content.ClipData.newPlainText("Code", note.code(target.copyIndex)))
                            } else target.url?.let { url ->
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }
                }
            }, contentScale = ContentScale.FillWidth)
    }
}
