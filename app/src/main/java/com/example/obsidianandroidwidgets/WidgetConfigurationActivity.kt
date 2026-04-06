package com.example.obsidianandroidwidgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.obsidianandroidwidgets.ui.theme.ObsidianAndroidWidgetsTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

private data class SelectedDocumentState(
    val uriString: String = "",
    val label: String = "",
)

private data class CachedWidgetConfiguration(
    val vault: SelectedDocumentState = SelectedDocumentState(),
    val note: SelectedDocumentState = SelectedDocumentState(),
    val appearanceMode: WidgetAppearanceMode = WidgetAppearanceMode.Dark,
    val customPalette: WidgetColorPalette = DARK_WIDGET_PALETTE
)

private const val CONFIG_CACHE_PREFS = "widgetConfigurationCache"
private const val CACHE_VAULT_URI_KEY = "cachedVaultUri"
private const val CACHE_VAULT_LABEL_KEY = "cachedVaultLabel"
private const val CACHE_NOTE_URI_KEY = "cachedNoteUri"
private const val CACHE_NOTE_LABEL_KEY = "cachedNoteLabel"
private const val CACHE_APPEARANCE_MODE_KEY = "cachedAppearanceMode"
private const val CACHE_TOOLBAR_BACKGROUND_KEY = "cachedToolbarBackground"
private const val CACHE_TOOLBAR_TEXT_KEY = "cachedToolbarText"
private const val CACHE_BODY_BACKGROUND_KEY = "cachedBodyBackground"
private const val CACHE_BODY_TEXT_KEY = "cachedBodyText"

class WidgetConfigurationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val cachedConfiguration = loadCachedWidgetConfiguration()
        val selectedVault = MutableStateFlow(cachedConfiguration.vault)
        val selectedNote = MutableStateFlow(cachedConfiguration.note)

        val getMarkdownFile =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                val uri = result.data?.data ?: return@registerForActivityResult
                if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult

                persistReadPermission(uri)
                selectedNote.value = SelectedDocumentState(
                    uriString = uri.toString(),
                    label = queryDisplayName(uri)
                        ?.removeSuffix(".md")
                        ?.removeSuffix(".markdown")
                        ?: PageWidget.resolveNoteTitle(this, uri.toString())
                )
            }

        val getVaultFolder =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                val uri = result.data?.data ?: return@registerForActivityResult
                if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult

                persistReadPermission(uri)
                selectedVault.value = SelectedDocumentState(
                    uriString = uri.toString(),
                    label = resolveTreeName(uri)
                )
            }

        setContent {
            val context = LocalContext.current
            val vaultState by selectedVault.collectAsState()
            val noteState by selectedNote.collectAsState()
            var selectedMode by remember { mutableStateOf(cachedConfiguration.appearanceMode) }
            var toolbarBackgroundHex by remember {
                mutableStateOf(cachedConfiguration.customPalette.toolbarBackgroundHex)
            }
            var toolbarTextHex by remember {
                mutableStateOf(cachedConfiguration.customPalette.toolbarTextHex)
            }
            var bodyBackgroundHex by remember {
                mutableStateOf(cachedConfiguration.customPalette.bodyBackgroundHex)
            }
            var bodyTextHex by remember {
                mutableStateOf(cachedConfiguration.customPalette.bodyTextHex)
            }
            val customPalette = WidgetColorPalette(
                toolbarBackgroundHex = toolbarBackgroundHex,
                toolbarTextHex = toolbarTextHex,
                bodyBackgroundHex = bodyBackgroundHex,
                bodyTextHex = bodyTextHex
            )
            val effectivePalette = selectedMode.resolvePalette(customPalette)
            val toolbarBackgroundColor = Color(
                parseAndroidColor(
                    effectivePalette.toolbarBackgroundHex,
                    DARK_WIDGET_PALETTE.toolbarBackgroundHex
                )
            )
            val toolbarTextColor = Color(
                parseAndroidColor(
                    effectivePalette.toolbarTextHex,
                    DARK_WIDGET_PALETTE.toolbarTextHex
                )
            )
            val bodyBackgroundColor = Color(
                parseAndroidColor(
                    effectivePalette.bodyBackgroundHex,
                    DARK_WIDGET_PALETTE.bodyBackgroundHex
                )
            )

            val sampleMarkdown = """
                ### Friday
                - ==Important:== Buy more eggs
                - **Buy twelve eggs**
                - *Buy two eggs*

                ```kotlin
                println("Buy eggs today!")
                ```
            """.trimIndent()

            val previewBitmap: Bitmap? = remember(
                context,
                effectivePalette.bodyBackgroundHex,
                effectivePalette.bodyTextHex
            ) {
                val safeBackground = normalizeColorHex(
                    effectivePalette.bodyBackgroundHex,
                    DARK_WIDGET_PALETTE.bodyBackgroundHex
                )
                val safeText = normalizeColorHex(
                    effectivePalette.bodyTextHex,
                    DARK_WIDGET_PALETTE.bodyTextHex
                )
                runCatching {
                    BitmapRenderer(context).renderBitmap(
                        markdown = sampleMarkdown,
                        width = 1000,
                        backgroundColor = parseAndroidColor(
                            safeBackground,
                            DARK_WIDGET_PALETTE.bodyBackgroundHex
                        ),
                        textColor = parseAndroidColor(
                            safeText,
                            DARK_WIDGET_PALETTE.bodyTextHex
                        )
                    )
                }.getOrNull()
            }

            ObsidianAndroidWidgetsTheme {
                val appWidgetId = intent?.extras?.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

                Column(modifier = Modifier.fillMaxSize()) {
                    filePicker(
                        filePath = vaultState.label.ifBlank { "No vault selected" },
                        buttonText = "Select Vault"
                    ) {
                        val pickVaultIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                            if (vaultState.uriString.isNotBlank()) {
                                putExtra(
                                    DocumentsContract.EXTRA_INITIAL_URI,
                                    Uri.parse(vaultState.uriString)
                                )
                            }
                        }
                        getVaultFolder.launch(pickVaultIntent)
                    }

                    filePicker(
                        filePath = noteState.label.ifBlank { "No page selected" },
                        buttonText = "Select Page"
                    ) {
                        if (vaultState.uriString.isBlank()) {
                            Toast.makeText(
                                context,
                                "Select the vault first so Android can grant access.",
                                Toast.LENGTH_LONG
                            ).show()
                            return@filePicker
                        }

                        val pickNoteIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                            type = "*/*"
                            putExtra(
                                Intent.EXTRA_MIME_TYPES,
                                arrayOf("text/markdown", "text/plain", "application/octet-stream")
                            )
                            putExtra(
                                DocumentsContract.EXTRA_INITIAL_URI,
                                Uri.parse(vaultState.uriString)
                            )
                        }
                        getMarkdownFile.launch(pickNoteIntent)
                    }

                    themeModePicker(
                        selectedMode = selectedMode,
                        onModeSelected = { selectedMode = it }
                    )

                    if (selectedMode == WidgetAppearanceMode.Custom) {
                        colorInputRow(
                            label = "Toolbar Background",
                            value = toolbarBackgroundHex,
                            onValueChange = { toolbarBackgroundHex = it }
                        )

                        colorInputRow(
                            label = "Toolbar Text",
                            value = toolbarTextHex,
                            onValueChange = { toolbarTextHex = it }
                        )

                        colorInputRow(
                            label = "Body Background",
                            value = bodyBackgroundHex,
                            onValueChange = { bodyBackgroundHex = it }
                        )

                        colorInputRow(
                            label = "Body Text",
                            value = bodyTextHex,
                            onValueChange = { bodyTextHex = it }
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(onClick = {
                            if (selectedMode == WidgetAppearanceMode.Custom) {
                                val colorFields = listOf(
                                    Triple(
                                        "Toolbar background",
                                        toolbarBackgroundHex,
                                        DARK_WIDGET_PALETTE.toolbarBackgroundHex
                                    ),
                                    Triple(
                                        "Toolbar text",
                                        toolbarTextHex,
                                        DARK_WIDGET_PALETTE.toolbarTextHex
                                    ),
                                    Triple(
                                        "Body background",
                                        bodyBackgroundHex,
                                        DARK_WIDGET_PALETTE.bodyBackgroundHex
                                    ),
                                    Triple(
                                        "Body text",
                                        bodyTextHex,
                                        DARK_WIDGET_PALETTE.bodyTextHex
                                    )
                                )

                                val invalidField = colorFields.firstOrNull { !isValidColorHex(it.second) }
                                if (invalidField != null) {
                                    Toast.makeText(
                                        baseContext,
                                        "${invalidField.first} colour must be like ${invalidField.third}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    return@Button
                                }
                            }

                            val safeCustomPalette = WidgetColorPalette(
                                toolbarBackgroundHex = normalizeColorHex(
                                    toolbarBackgroundHex,
                                    DARK_WIDGET_PALETTE.toolbarBackgroundHex
                                ),
                                toolbarTextHex = normalizeColorHex(
                                    toolbarTextHex,
                                    DARK_WIDGET_PALETTE.toolbarTextHex
                                ),
                                bodyBackgroundHex = normalizeColorHex(
                                    bodyBackgroundHex,
                                    DARK_WIDGET_PALETTE.bodyBackgroundHex
                                ),
                                bodyTextHex = normalizeColorHex(
                                    bodyTextHex,
                                    DARK_WIDGET_PALETTE.bodyTextHex
                                )
                            )
                            val savedPalette = selectedMode.resolvePalette(safeCustomPalette)

                            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                                Toast.makeText(
                                    baseContext,
                                    "The widget id is missing. Add the widget again.",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@Button
                            }
                            if (noteState.uriString.isBlank()) {
                                Toast.makeText(
                                    baseContext,
                                    "Select a page to show in the widget.",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@Button
                            }
                            if (vaultState.uriString.isBlank()) {
                                Toast.makeText(
                                    baseContext,
                                    "Select the parent vault folder.",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@Button
                            }

                            saveCachedWidgetConfiguration(
                                CachedWidgetConfiguration(
                                    vault = vaultState,
                                    note = noteState,
                                    appearanceMode = selectedMode,
                                    customPalette = safeCustomPalette
                                )
                            )

                            CoroutineScope(Dispatchers.IO).launch {
                                val manager = GlanceAppWidgetManager(baseContext)
                                val glanceId = manager.getGlanceIdBy(appWidgetId)
                                val noteTitle = noteState.label.ifBlank {
                                    PageWidget.resolveNoteTitle(context, noteState.uriString)
                                }
                                val vaultName = vaultState.label.ifBlank {
                                    PageWidget.resolveVaultName(context, vaultState.uriString)
                                }
                                val obsidianFilePath = PageWidget.deriveObsidianFilePath(
                                    noteState.uriString,
                                    vaultState.uriString,
                                    noteTitle
                                )
                                val noteText = PageWidget.readNoteText(context, noteState.uriString)

                                updateAppWidgetState(context, glanceId) { prefs ->
                                    prefs[PageWidget.noteUriKey] = noteState.uriString
                                    prefs[PageWidget.vaultUriKey] = vaultState.uriString
                                    prefs[PageWidget.noteTitleKey] = noteTitle
                                    prefs[PageWidget.vaultNameKey] = vaultName
                                    prefs[PageWidget.obsidianFilePathKey] = obsidianFilePath
                                    prefs[PageWidget.textKey] = noteText
                                    prefs[PageWidget.appearanceModeKey] = selectedMode.storageValue
                                    prefs[PageWidget.toolbarBackgroundColorHexKey] =
                                        safeCustomPalette.toolbarBackgroundHex
                                    prefs[PageWidget.toolbarTextColorHexKey] =
                                        safeCustomPalette.toolbarTextHex
                                    prefs[PageWidget.bodyBackgroundColorHexKey] =
                                        safeCustomPalette.bodyBackgroundHex
                                    prefs[PageWidget.bodyTextColorHexKey] =
                                        safeCustomPalette.bodyTextHex
                                    prefs[PageWidget.backgroundColorHexKey] = savedPalette.bodyBackgroundHex
                                    prefs[PageWidget.textColorHexKey] = savedPalette.bodyTextHex
                                }

                                PageWidget.update(context, glanceId)
                            }

                            val resultValue = Intent().putExtra(
                                AppWidgetManager.EXTRA_APPWIDGET_ID,
                                appWidgetId
                            )
                            setResult(Activity.RESULT_OK, resultValue)
                            startWorkManager(context)
                            finish()
                        }) {
                            Text("Complete")
                        }
                    }

                    if (previewBitmap != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(toolbarBackgroundColor)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 10.dp, top = 2.dp, end = 10.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = noteState.label.ifBlank { "Preview Title" },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 3.5.dp),
                                    color = toolbarTextColor,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Image(
                                    painter = painterResource(R.drawable.baseline_refresh_24),
                                    contentDescription = "Refresh preview",
                                    modifier = Modifier.size(30.dp),
                                    colorFilter = ColorFilter.tint(toolbarTextColor)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 0.5.dp, end = 0.5.dp, top = 0.5.dp, bottom = 0.5.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(bodyBackgroundColor)
                            ) {
                                Image(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 4.dp),
                                    bitmap = previewBitmap.asImageBitmap(),
                                    contentDescription = "Rendered markdown preview",
                                    alignment = Alignment.TopStart,
                                    contentScale = ContentScale.FillWidth
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun persistReadPermission(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val nameIndex =
                    cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun resolveTreeName(uri: Uri): String {
        return runCatching {
            val documentUri = DocumentsContract.buildDocumentUriUsingTree(
                uri,
                DocumentsContract.getTreeDocumentId(uri)
            )
            queryDisplayName(documentUri)
                ?: DocumentsContract.getTreeDocumentId(uri).substringAfter(':').substringBefore('/')
        }.getOrDefault("Selected vault")
    }

    private fun startWorkManager(context: Context) {
        val request = PeriodicWorkRequestBuilder<UpdateWidgetWorker>(
            15,
            TimeUnit.MINUTES,
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun loadCachedWidgetConfiguration(): CachedWidgetConfiguration {
        val prefs = getSharedPreferences(CONFIG_CACHE_PREFS, Context.MODE_PRIVATE)
        return CachedWidgetConfiguration(
            vault = SelectedDocumentState(
                uriString = prefs.getString(CACHE_VAULT_URI_KEY, "").orEmpty(),
                label = prefs.getString(CACHE_VAULT_LABEL_KEY, "").orEmpty()
            ),
            note = SelectedDocumentState(
                uriString = prefs.getString(CACHE_NOTE_URI_KEY, "").orEmpty(),
                label = prefs.getString(CACHE_NOTE_LABEL_KEY, "").orEmpty()
            ),
            appearanceMode = WidgetAppearanceMode.fromStorage(
                prefs.getString(CACHE_APPEARANCE_MODE_KEY, WidgetAppearanceMode.Dark.storageValue)
            ),
            customPalette = WidgetColorPalette(
                toolbarBackgroundHex = normalizeColorHex(
                    prefs.getString(
                        CACHE_TOOLBAR_BACKGROUND_KEY,
                        DARK_WIDGET_PALETTE.toolbarBackgroundHex
                    ).orEmpty(),
                    DARK_WIDGET_PALETTE.toolbarBackgroundHex
                ),
                toolbarTextHex = normalizeColorHex(
                    prefs.getString(
                        CACHE_TOOLBAR_TEXT_KEY,
                        DARK_WIDGET_PALETTE.toolbarTextHex
                    ).orEmpty(),
                    DARK_WIDGET_PALETTE.toolbarTextHex
                ),
                bodyBackgroundHex = normalizeColorHex(
                    prefs.getString(
                        CACHE_BODY_BACKGROUND_KEY,
                        DARK_WIDGET_PALETTE.bodyBackgroundHex
                    ).orEmpty(),
                    DARK_WIDGET_PALETTE.bodyBackgroundHex
                ),
                bodyTextHex = normalizeColorHex(
                    prefs.getString(
                        CACHE_BODY_TEXT_KEY,
                        DARK_WIDGET_PALETTE.bodyTextHex
                    ).orEmpty(),
                    DARK_WIDGET_PALETTE.bodyTextHex
                )
            )
        )
    }

    private fun saveCachedWidgetConfiguration(config: CachedWidgetConfiguration) {
        getSharedPreferences(CONFIG_CACHE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(CACHE_VAULT_URI_KEY, config.vault.uriString)
            .putString(CACHE_VAULT_LABEL_KEY, config.vault.label)
            .putString(CACHE_NOTE_URI_KEY, config.note.uriString)
            .putString(CACHE_NOTE_LABEL_KEY, config.note.label)
            .putString(CACHE_APPEARANCE_MODE_KEY, config.appearanceMode.storageValue)
            .putString(
                CACHE_TOOLBAR_BACKGROUND_KEY,
                normalizeColorHex(
                    config.customPalette.toolbarBackgroundHex,
                    DARK_WIDGET_PALETTE.toolbarBackgroundHex
                )
            )
            .putString(
                CACHE_TOOLBAR_TEXT_KEY,
                normalizeColorHex(
                    config.customPalette.toolbarTextHex,
                    DARK_WIDGET_PALETTE.toolbarTextHex
                )
            )
            .putString(
                CACHE_BODY_BACKGROUND_KEY,
                normalizeColorHex(
                    config.customPalette.bodyBackgroundHex,
                    DARK_WIDGET_PALETTE.bodyBackgroundHex
                )
            )
            .putString(
                CACHE_BODY_TEXT_KEY,
                normalizeColorHex(
                    config.customPalette.bodyTextHex,
                    DARK_WIDGET_PALETTE.bodyTextHex
                )
            )
            .apply()
    }
}

@Composable
private fun themeModePicker(
    selectedMode: WidgetAppearanceMode,
    onModeSelected: (WidgetAppearanceMode) -> Unit,
) {
    Text(
        text = "Theme",
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        WidgetAppearanceMode.values().forEach { mode ->
            val isSelected = mode == selectedMode
            Button(
                onClick = { onModeSelected(mode) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) Color(0xFF3A6EA5) else Color(0xFF4A4A4A),
                    contentColor = Color.White
                )
            ) {
                Text(mode.label)
            }
        }
    }
}

@Composable
fun filePicker(filePath: String, buttonText: String, onClick: () -> Unit) {
    val scroll = rememberScrollState(0)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            filePath,
            modifier = Modifier
                .width(250.dp)
                .horizontalScroll(scroll)
        )
        Button(onClick = onClick, modifier = Modifier.padding(5.dp)) {
            Text(text = buttonText)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun colorInputRow(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

private fun normalizeColorHex(color: String, defaultColor: String): String {
    val trimmed = color.trim()
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

private fun isValidColorHex(color: String): Boolean {
    val trimmed = color.trim()
    val withHash = if (trimmed.startsWith("#")) trimmed else "#$trimmed"
    return withHash.matches(Regex("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$"))
}

private fun parseAndroidColor(colorHex: String, defaultColor: String): Int {
    return try {
        android.graphics.Color.parseColor(normalizeColorHex(colorHex, defaultColor))
    } catch (_: IllegalArgumentException) {
        android.graphics.Color.parseColor(defaultColor)
    }
}
