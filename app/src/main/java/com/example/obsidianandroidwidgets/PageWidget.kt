package com.example.obsidianandroidwidgets

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.text.TextPaint
import android.text.TextUtils
import android.graphics.Typeface
import android.util.Log
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
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
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.work.WorkManager
import java.io.FileNotFoundException
import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.delay

object PageWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override val stateDefinition = PreferencesGlanceStateDefinition

    private const val TAG = "PageWidget"
    private const val TOOLBAR_ICON_SIZE_DP = 30
    private const val PADDING_SIZE_DP = 3.5f
    private const val OUTER_CORNER_RADIUS_DP = 22
    private const val TOOLBAR_HORIZONTAL_PADDING_DP = 10
    private const val TOOLBAR_TOP_PADDING_DP = 2
    private const val TOOLBAR_BOTTOM_PADDING_DP = 6
    private const val BODY_FRAME_INSET_DP = 0.5f
    private const val BODY_TOP_INSET_DP = 0.5f
    private const val BODY_CONTENT_HORIZONTAL_PADDING_DP = 4
    private const val BODY_CONTENT_VERTICAL_PADDING_DP = 4
    private const val MIN_RENDER_WIDTH_DP = 120f
    private const val TOOLBAR_TITLE_SIZE_SP = 22f
    const val MIN_REFRESH_INDICATOR_MS = 500L
    private const val NOT_CONFIGURED_MESSAGE =
        "Widget not configured yet.\nRe-add or configure it to pick an Obsidian note."
    private const val EMPTY_NOTE_MESSAGE = "The selected note is empty."

    val noteUriKey = stringPreferencesKey("noteUri")
    val vaultUriKey = stringPreferencesKey("vaultUri")
    val noteTitleKey = stringPreferencesKey("noteTitle")
    val vaultNameKey = stringPreferencesKey("vaultName")
    val obsidianFilePathKey = stringPreferencesKey("obsidianFilePath")
    val textKey = stringPreferencesKey("textKey")
    val refreshInProgressKey = booleanPreferencesKey("refreshInProgress")
    val appearanceModeKey = stringPreferencesKey("appearanceMode")
    val toolbarBackgroundColorHexKey = stringPreferencesKey("toolbarBackgroundColorHex")
    val toolbarTextColorHexKey = stringPreferencesKey("toolbarTextColorHex")
    val bodyBackgroundColorHexKey = stringPreferencesKey("bodyBackgroundColorHex")
    val bodyTextColorHexKey = stringPreferencesKey("bodyTextColorHex")

    // Legacy single-surface colors retained for migration from older widget builds.
    val backgroundColorHexKey = stringPreferencesKey("backgroundColorHex")
    val textColorHexKey = stringPreferencesKey("textColorHex")

    private val legacyMdFilePathKey = stringPreferencesKey("mdFilePathKey")
    private val legacyVaultPathKey = stringPreferencesKey("vaultPathKey")

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        super.onDelete(context, glanceId)

        val widgetCount = GlanceAppWidgetManager(context).getGlanceIds(PageWidget::class.java).size
        if (widgetCount == 0) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val noteUri = currentState(key = noteUriKey).orEmpty()
            val vaultUri = currentState(key = vaultUriKey).orEmpty()
            val noteTitle = currentState(key = noteTitleKey)
                ?.takeIf { it.isNotBlank() }
                ?: resolveNoteTitle(context, noteUri)
            val vaultName = currentState(key = vaultNameKey)
                ?.takeIf { it.isNotBlank() }
                ?: resolveVaultName(context, vaultUri)
            val obsidianFilePath = currentState(key = obsidianFilePathKey)
                ?.takeIf { it.isNotBlank() }
                ?: deriveObsidianFilePath(noteUri, vaultUri, noteTitle)
            val legacyPath = currentState(key = legacyMdFilePathKey).orEmpty()
            val legacyVault = currentState(key = legacyVaultPathKey).orEmpty()
            val legacyBackgroundHex = currentState(key = backgroundColorHexKey)
            val legacyTextHex = currentState(key = textColorHexKey)
            val storedAppearanceMode = currentState(key = appearanceModeKey)
            val hasSavedCustomPalette = listOf(
                currentState(key = toolbarBackgroundColorHexKey),
                currentState(key = toolbarTextColorHexKey),
                currentState(key = bodyBackgroundColorHexKey),
                currentState(key = bodyTextColorHexKey),
                legacyBackgroundHex,
                legacyTextHex
            ).any { !it.isNullOrBlank() }
            val appearanceMode = if (storedAppearanceMode == null && hasSavedCustomPalette) {
                WidgetAppearanceMode.Custom
            } else {
                WidgetAppearanceMode.fromStorage(storedAppearanceMode)
            }
            val customPalette = WidgetColorPalette(
                toolbarBackgroundHex = normalizeColorHex(
                    currentState(key = toolbarBackgroundColorHexKey) ?: legacyBackgroundHex,
                    DARK_WIDGET_PALETTE.toolbarBackgroundHex
                ),
                toolbarTextHex = normalizeColorHex(
                    currentState(key = toolbarTextColorHexKey) ?: legacyTextHex,
                    DARK_WIDGET_PALETTE.toolbarTextHex
                ),
                bodyBackgroundHex = normalizeColorHex(
                    currentState(key = bodyBackgroundColorHexKey) ?: legacyBackgroundHex,
                    DARK_WIDGET_PALETTE.bodyBackgroundHex
                ),
                bodyTextHex = normalizeColorHex(
                    currentState(key = bodyTextColorHexKey) ?: legacyTextHex,
                    DARK_WIDGET_PALETTE.bodyTextHex
                )
            )
            val palette = appearanceMode.resolvePalette(customPalette)
            val toolbarBackgroundColorInt = parseAndroidColor(
                palette.toolbarBackgroundHex,
                DARK_WIDGET_PALETTE.toolbarBackgroundHex
            )
            val toolbarTextColorInt = parseAndroidColor(
                palette.toolbarTextHex,
                DARK_WIDGET_PALETTE.toolbarTextHex
            )
            val bodyBackgroundColorInt = parseAndroidColor(
                palette.bodyBackgroundHex,
                DARK_WIDGET_PALETTE.bodyBackgroundHex
            )
            val bodyTextColorInt = parseAndroidColor(
                palette.bodyTextHex,
                DARK_WIDGET_PALETTE.bodyTextHex
            )
            val toolbarTextColorProvider = ColorProvider(composeColor(toolbarTextColorInt))
            val isRefreshing = currentState(key = refreshInProgressKey) ?: false

            val text = currentState(key = textKey)
                ?.takeIf { it.isNotBlank() }
                ?: when {
                    noteUri.isNotBlank() -> readNoteText(context, noteUri)
                    legacyPath.isNotBlank() || legacyVault.isNotBlank() ->
                        "This widget uses an older storage-path setup.\nPlease reconfigure it once."

                    else -> NOT_CONFIGURED_MESSAGE
                }

            val renderer = BitmapRenderer(context)
            val availableWidthDp = (
                LocalSize.current.width.value -
                    (PADDING_SIZE_DP * 2f) -
                    (BODY_FRAME_INSET_DP * 2f) -
                    (BODY_CONTENT_HORIZONTAL_PADDING_DP * 2f)
                )
                .coerceAtLeast(MIN_RENDER_WIDTH_DP)
            val bitmapWidth = context.toPx(availableWidthDp).toInt()

            val toolbarTitle = truncateTitleForToolbar(
                context = context,
                title = noteTitle.ifBlank { "Obsidian note" },
                widgetWidthDp = LocalSize.current.width.value
            )
            val openNoteAction = buildOpenNoteAction(context, vaultName, obsidianFilePath)

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(PADDING_SIZE_DP.dp)
                    .cornerRadius(OUTER_CORNER_RADIUS_DP.dp)
                    .background(composeColor(toolbarBackgroundColorInt)),
            ) {
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(
                            start = TOOLBAR_HORIZONTAL_PADDING_DP.dp,
                            top = TOOLBAR_TOP_PADDING_DP.dp,
                            end = TOOLBAR_HORIZONTAL_PADDING_DP.dp,
                            bottom = TOOLBAR_BOTTOM_PADDING_DP.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = toolbarTitle,
                        modifier = GlanceModifier
                            .defaultWeight()
                            .padding(end = PADDING_SIZE_DP.dp),
                        style = TextStyle(
                            fontSize = TOOLBAR_TITLE_SIZE_SP.sp,
                            color = toolbarTextColorProvider,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )

                    if (isRefreshing) {
                        val spinnerRemoteViews = RemoteViews(
                            LocalContext.current.packageName,
                            R.layout.widget_toolbar_spinner
                        ).apply {
                            setColorStateList(
                                R.id.toolbarSpinner,
                                "setIndeterminateTintList",
                                ColorStateList.valueOf(toolbarTextColorInt)
                            )
                        }
                        AndroidRemoteViews(
                            spinnerRemoteViews,
                            modifier = GlanceModifier.size(TOOLBAR_ICON_SIZE_DP.dp)
                        )
                    } else {
                        Image(
                            provider = ImageProvider(R.drawable.baseline_refresh_24),
                            colorFilter = androidx.glance.ColorFilter.tint(toolbarTextColorProvider),
                            contentDescription = "Refresh note",
                            modifier = GlanceModifier
                                .clickable(actionRunCallback<ReloadWidget>())
                                .size(TOOLBAR_ICON_SIZE_DP.dp)
                        )
                    }
                }

                LazyColumn(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .padding(
                            start = BODY_FRAME_INSET_DP.dp,
                            end = BODY_FRAME_INSET_DP.dp,
                            top = BODY_TOP_INSET_DP.dp,
                            bottom = BODY_FRAME_INSET_DP.dp
                        )
                        .cornerRadius(OUTER_CORNER_RADIUS_DP.dp)
                        .background(composeColor(bodyBackgroundColorInt))
                ) {
                    item {
                        val remoteView = RemoteViews(LocalContext.current.packageName, R.layout.test_layout)
                        remoteView.setImageViewBitmap(
                            R.id.imageView,
                            renderer.renderBitmap(
                                markdown = text,
                                width = bitmapWidth,
                                backgroundColor = bodyBackgroundColorInt,
                                textColor = bodyTextColorInt
                            )
                        )
                        AndroidRemoteViews(
                            remoteView,
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .padding(
                                    start = BODY_CONTENT_HORIZONTAL_PADDING_DP.dp,
                                    end = BODY_CONTENT_HORIZONTAL_PADDING_DP.dp,
                                    top = BODY_CONTENT_VERTICAL_PADDING_DP.dp,
                                    bottom = BODY_CONTENT_VERTICAL_PADDING_DP.dp
                                )
                                .clickableIf(openNoteAction)
                        )
                    }
                }
            }
        }
    }

    fun readNoteText(context: Context, noteUriString: String): String {
        if (noteUriString.isBlank()) {
            return NOT_CONFIGURED_MESSAGE
        }

        return try {
            val noteUri = Uri.parse(noteUriString)
            val text = context.contentResolver.openInputStream(noteUri)?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                ?.trimEnd()
                .orEmpty()

            if (text.isBlank()) EMPTY_NOTE_MESSAGE else text
        } catch (_: FileNotFoundException) {
            "Unable to open the selected note.\nReconfigure the widget and pick the note again."
        } catch (_: SecurityException) {
            "The app no longer has permission to read this note.\nReconfigure the widget to restore access."
        } catch (_: IOException) {
            "The note could not be read right now.\nTry refreshing or reconfiguring the widget."
        } catch (_: IllegalArgumentException) {
            "The saved note reference is invalid.\nReconfigure the widget."
        } catch (error: Exception) {
            Log.e(TAG, "Unexpected error while reading note text", error)
            "The note could not be loaded.\nTry reconfiguring the widget."
        }
    }

    fun resolveNoteTitle(context: Context, noteUriString: String): String {
        if (noteUriString.isBlank()) {
            return ""
        }

        return queryDisplayName(context, noteUriString)
            ?.removeSuffix(".md")
            ?.removeSuffix(".markdown")
            .orEmpty()
    }

    fun resolveVaultName(context: Context, vaultUriString: String): String {
        if (vaultUriString.isBlank()) {
            return ""
        }

        return queryTreeDisplayName(context, vaultUriString).orEmpty()
    }

    fun deriveObsidianFilePath(
        noteUriString: String,
        vaultUriString: String,
        fallbackTitle: String = "",
    ): String {
        if (noteUriString.isBlank()) {
            return fallbackTitle.removeMarkdownExtension()
        }

        return try {
            val noteDocumentId = DocumentsContract.getDocumentId(Uri.parse(noteUriString))
            val vaultDocumentId = if (vaultUriString.isBlank()) {
                ""
            } else {
                DocumentsContract.getTreeDocumentId(Uri.parse(vaultUriString))
            }

            val relativePath = when {
                vaultDocumentId.isNotBlank() && noteDocumentId.startsWith("$vaultDocumentId/") ->
                    noteDocumentId.removePrefix("$vaultDocumentId/")

                ':' in noteDocumentId -> noteDocumentId.substringAfter(':')
                else -> noteDocumentId
            }

            relativePath.removeMarkdownExtension()
        } catch (_: IllegalArgumentException) {
            fallbackTitle.removeMarkdownExtension()
        }
    }

    private fun buildOpenNoteAction(
        context: Context,
        vaultName: String,
        obsidianFilePath: String,
    ): Action? {
        if (vaultName.isBlank() || obsidianFilePath.isBlank()) {
            return null
        }

        val openUri = Uri.parse(
            "obsidian://open?vault=${encodeUriValue(vaultName)}&file=${encodeUriValue(obsidianFilePath)}"
        )

        return buildActivityAction(context, openUri)
    }

    private fun buildActivityAction(context: Context, uri: Uri): Action? {
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val componentName = intent.resolveActivity(context.packageManager) ?: return null
        intent.component = componentName
        intent.`package` = componentName.packageName
        return actionStartActivity(intent)
    }

    private fun queryDisplayName(context: Context, uriString: String): String? {
        return try {
            context.contentResolver.query(
                Uri.parse(uriString),
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
            }
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun queryTreeDisplayName(context: Context, treeUriString: String): String? {
        return try {
            val treeUri = Uri.parse(treeUriString)
            val documentUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri)
            )
            queryDisplayName(context, documentUri.toString())
                ?: DocumentsContract.getTreeDocumentId(treeUri)
                    .substringAfter(':')
                    .substringBefore('/')
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun truncateTitleForToolbar(
        context: Context,
        title: String,
        widgetWidthDp: Float,
    ): String {
        val reservedWidthDp =
            (PADDING_SIZE_DP * 2f) +
                (TOOLBAR_HORIZONTAL_PADDING_DP * 2f) +
                TOOLBAR_ICON_SIZE_DP +
                PADDING_SIZE_DP
        val availableTitleWidthPx = context.toPx((widgetWidthDp - reservedWidthDp).coerceAtLeast(72f))
        val paint = TextPaint().apply {
            isAntiAlias = true
            textSize = context.spToPx(TOOLBAR_TITLE_SIZE_SP)
            typeface = Typeface.DEFAULT_BOLD
        }

        return TextUtils.ellipsize(
            title,
            paint,
            availableTitleWidthPx,
            TextUtils.TruncateAt.END
        ).toString()
    }

    private fun normalizeColorHex(color: String?, defaultColor: String): String {
        val trimmed = color?.trim().orEmpty()
        if (trimmed.isBlank()) {
            return defaultColor
        }

        val withHash = if (trimmed.startsWith("#")) trimmed else "#$trimmed"
        return if (withHash.matches(Regex("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$"))) {
            withHash.uppercase()
        } else {
            defaultColor
        }
    }

    private fun parseAndroidColor(colorHex: String, defaultColor: String): Int {
        return try {
            android.graphics.Color.parseColor(normalizeColorHex(colorHex, defaultColor))
        } catch (_: IllegalArgumentException) {
            android.graphics.Color.parseColor(defaultColor)
        }
    }

    private fun composeColor(androidColor: Int): Color = Color(androidColor)

    private fun Context.toPx(dp: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp,
        resources.displayMetrics
    )

    private fun Context.spToPx(sp: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        sp,
        resources.displayMetrics
    )

    private fun encodeUriValue(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    private fun String.removeMarkdownExtension(): String = when {
        endsWith(".markdown", ignoreCase = true) -> dropLast(".markdown".length)
        endsWith(".md", ignoreCase = true) -> dropLast(3)
        else -> this
    }

    private fun GlanceModifier.clickableIf(action: Action?): GlanceModifier {
        return if (action == null) this else clickable(action)
    }
}

class SimplePageWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget
        get() = PageWidget
}

class ReloadWidget : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        var noteUri = ""
        updateAppWidgetState(context, glanceId) { prefs ->
            noteUri = prefs[PageWidget.noteUriKey].orEmpty()
            prefs[PageWidget.refreshInProgressKey] = true
        }
        PageWidget.update(context, glanceId)

        val refreshStartedAt = SystemClock.elapsedRealtime()
        val refreshedText = PageWidget.readNoteText(context, noteUri)
        val elapsedMs = SystemClock.elapsedRealtime() - refreshStartedAt
        val remainingIndicatorMs = PageWidget.MIN_REFRESH_INDICATOR_MS - elapsedMs
        if (remainingIndicatorMs > 0) {
            delay(remainingIndicatorMs)
        }

        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[PageWidget.textKey] = refreshedText
            prefs[PageWidget.refreshInProgressKey] = false
        }
        PageWidget.update(context, glanceId)
    }
}
