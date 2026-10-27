package com.example.obsidianandroidwidgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.example.obsidianandroidwidgets.ui.theme.ObsidianAndroidWidgetsTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class SelectedDocumentState(
    val uriString: String = "",
    val label: String = "",
)

private data class CachedWidgetConfiguration(
    val vault: SelectedDocumentState = SelectedDocumentState(),
    val note: SelectedDocumentState = SelectedDocumentState(),
    val appearanceMode: WidgetAppearanceMode = WidgetAppearanceMode.Dark,
    val customPalette: WidgetColorPalette = DARK_WIDGET_PALETTE,
    val accentMode: String = "default",
    val customAccentHex: String = "#8A5CF5",
    val updateMinutes: Long = 15L,
    val customPaletteSet: Boolean = false,
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
        val widgetHostPackage = callingPackage ?: referrer?.host
        val getMarkdownFile =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                val uri = result.data?.data ?: return@registerForActivityResult
                if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult

                persistDocumentPermission(uri, result.data?.flags ?: 0)
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

                persistDocumentPermission(uri, result.data?.flags ?: 0)
                selectedVault.value = SelectedDocumentState(
                    uriString = uri.toString(),
                    label = resolveTreeName(uri)
                )
            }

        setContent {
            val context = LocalContext.current
            val vaultState by selectedVault.collectAsState()
            val noteState by selectedNote.collectAsState()
            var defaultAccent by remember { mutableStateOf(ObsidianTheme.DEFAULT_ACCENT_COLOR) }
            var accentMode by remember { mutableStateOf(cachedConfiguration.accentMode) }
            var customAccentHex by remember { mutableStateOf(cachedConfiguration.customAccentHex) }
            var updateMinutes by remember { mutableStateOf(cachedConfiguration.updateMinutes) }
            LaunchedEffect(vaultState.uriString) {
                val loaded = withContext(Dispatchers.IO) {
                    ObsidianTheme.accentColor(context, vaultState.uriString, 0)
                }
                defaultAccent = loaded.takeUnless { it == 0 } ?: ObsidianTheme.DEFAULT_ACCENT_COLOR
            }
            var selectedMode by remember { mutableStateOf(cachedConfiguration.appearanceMode) }
            var customPaletteSet by remember { mutableStateOf(cachedConfiguration.customPaletteSet) }
            var lastPreset by remember { mutableStateOf(if (cachedConfiguration.appearanceMode == WidgetAppearanceMode.Light ||
                cachedConfiguration.customPalette == LIGHT_WIDGET_PALETTE) WidgetAppearanceMode.Light else WidgetAppearanceMode.Dark) }
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

            ObsidianAndroidWidgetsTheme {
                val appWidgetId = intent?.extras?.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Widget setup", style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(vertical = 8.dp))
                    filePicker(
                        filePath = vaultState.label.ifBlank { "No vault selected" },
                        buttonText = "Select Vault"
                    ) {
                        val pickVaultIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
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
                            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
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
                        onModeSelected = { mode ->
                            if (mode == WidgetAppearanceMode.Custom) {
                                val seed = customPaletteOnEntry(lastPreset, customPalette, customPaletteSet)
                                toolbarBackgroundHex = seed.toolbarBackgroundHex
                                toolbarTextHex = seed.toolbarTextHex
                                bodyBackgroundHex = seed.bodyBackgroundHex
                                bodyTextHex = seed.bodyTextHex
                            }
                            if (mode != WidgetAppearanceMode.Custom) lastPreset = mode
                            selectedMode = mode
                        },
                        onReset = {
                            toolbarBackgroundHex = DARK_WIDGET_PALETTE.toolbarBackgroundHex
                            toolbarTextHex = DARK_WIDGET_PALETTE.toolbarTextHex
                            bodyBackgroundHex = DARK_WIDGET_PALETTE.bodyBackgroundHex
                            bodyTextHex = DARK_WIDGET_PALETTE.bodyTextHex
                            customPaletteSet = false
                            selectedMode = WidgetAppearanceMode.Dark
                            lastPreset = WidgetAppearanceMode.Dark
                        }
                    )

                    if (selectedMode == WidgetAppearanceMode.Custom) {
                        colorInputRow(
                            label = "Toolbar Background",
                            value = toolbarBackgroundHex,
                            onValueChange = { toolbarBackgroundHex = it; customPaletteSet = true }
                        )

                        colorInputRow(
                            label = "Toolbar Text",
                            value = toolbarTextHex,
                            onValueChange = { toolbarTextHex = it; customPaletteSet = true }
                        )

                        colorInputRow(
                            label = "Body Background",
                            value = bodyBackgroundHex,
                            onValueChange = { bodyBackgroundHex = it; customPaletteSet = true }
                        )

                        colorInputRow(
                            label = "Body Text",
                            value = bodyTextHex,
                            onValueChange = { bodyTextHex = it; customPaletteSet = true }
                        )
                    }

                    Text("Accent colour", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("default" to "Default", "custom" to "Custom").forEach { (value, label) ->
                            Button(onClick = {
                                accentMode = value
                            }, colors = ButtonDefaults.buttonColors(
                                containerColor = if (accentMode == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (accentMode == value) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )) { Text(label) }
                        }
                    }
                    if (accentMode == "custom") {
                        colorInputRow("Accent hex colour", customAccentHex) { customAccentHex = it }
                    }
                    Text("Check for updates", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp))
                    refreshIntervalPicker(updateMinutes) { updateMinutes = it }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(onClick = {
                            if (accentMode == "custom" && !isValidColorHex(customAccentHex)) {
                                Toast.makeText(context, "Accent colour must be a hex colour, e.g. #00ABA9", Toast.LENGTH_LONG).show()
                                return@Button
                            }
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
                                    customPalette = safeCustomPalette,
                                    accentMode = accentMode,
                                    customAccentHex = normalizeColorHex(customAccentHex, "#8A5CF5"),
                                    updateMinutes = updateMinutes,
                                    customPaletteSet = customPaletteSet
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
                                ObsidianTheme.readAccentColor(context, vaultState.uriString, 0)

                                updateAppWidgetState(context, glanceId) { prefs ->
                                    prefs[PageWidget.noteUriKey] = noteState.uriString
                                    prefs[PageWidget.vaultUriKey] = vaultState.uriString
                                    if (widgetHostPackage != null) prefs[PageWidget.hostPackageKey] = widgetHostPackage
                                    prefs[PageWidget.noteTitleKey] = noteTitle
                                    prefs[PageWidget.vaultNameKey] = vaultName
                                    prefs[PageWidget.obsidianFilePathKey] = obsidianFilePath
                                    prefs[PageWidget.textKey] = noteText
                                    prefs[PageWidget.appearanceModeKey] = selectedMode.storageValue
                                    prefs[PageWidget.accentModeKey] = accentMode
                                    prefs[PageWidget.customAccentHexKey] = normalizeColorHex(customAccentHex, "#8A5CF5")
                                    prefs[PageWidget.updateMinutesKey] = updateMinutes
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

                                refreshNoteSnapshot(context, glanceId)
                                PageWidget.update(context, glanceId)
                                WidgetRefreshSchedule.reschedule(context)
                            }

                            val resultValue = Intent().putExtra(
                                AppWidgetManager.EXTRA_APPWIDGET_ID,
                                appWidgetId
                            )
                            setResult(Activity.RESULT_OK, resultValue)
                            finish()
                        }) {
                            Text("Complete")
                        }
                    }

                    Text("Preview", style = MaterialTheme.typography.titleMedium)
                    run {
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
                                    text = "Example",
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
                                SampleNotePreview(background = bodyBackgroundColor,
                                    text = Color(parseAndroidColor(effectivePalette.bodyTextHex, DARK_WIDGET_PALETTE.bodyTextHex)),
                                    accent = if (accentMode == "custom") parseAndroidColor(customAccentHex, "#8A5CF5") else defaultAccent,
                                    vaultName = vaultState.label)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun persistDocumentPermission(uri: Uri, grantedFlags: Int) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                grantedFlags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
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

    private fun loadCachedWidgetConfiguration(): CachedWidgetConfiguration {
        val prefs = getSharedPreferences(CONFIG_CACHE_PREFS, Context.MODE_PRIVATE)
        return CachedWidgetConfiguration(
            customPaletteSet = prefs.getBoolean("customPaletteSet", listOf(CACHE_TOOLBAR_BACKGROUND_KEY,
                CACHE_TOOLBAR_TEXT_KEY, CACHE_BODY_BACKGROUND_KEY, CACHE_BODY_TEXT_KEY).any { prefs.contains(it) }),
            accentMode = prefs.getString("accentMode", "default").orEmpty(),
            customAccentHex = prefs.getString("customAccentHex", "#8A5CF5").orEmpty(),
            updateMinutes = prefs.getLong("updateMinutes", 15L).takeIf { it in WidgetRefreshSchedule.options } ?: 15L,
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
            .putString("accentMode", config.accentMode)
            .putString("customAccentHex", config.customAccentHex)
            .putLong("updateMinutes", config.updateMinutes)
            .putBoolean("customPaletteSet", config.customPaletteSet)
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
    onReset: () -> Unit,
) {
    Text(
        text = "Theme",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 12.dp)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WidgetAppearanceMode.values().forEach { mode ->
            val isSelected = mode == selectedMode
            Button(
                onClick = { onModeSelected(mode) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(mode.label)
            }
        }
        if (selectedMode == WidgetAppearanceMode.Custom) {
            IconButton(onClick = onReset, modifier = Modifier.size(36.dp)) {
                Icon(painterResource(R.drawable.baseline_refresh_24), "Reset custom theme")
            }
        }
    }
}

@Composable
fun filePicker(filePath: String, buttonText: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            filePath,
            modifier = Modifier
                .weight(1f).padding(end = 12.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Button(onClick = onClick, modifier = Modifier.padding(5.dp)) {
            Text(text = buttonText, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun refreshIntervalPicker(minutes: Long, onSelected: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text(WidgetRefreshSchedule.label(minutes)) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            WidgetRefreshSchedule.options.forEach { value ->
                DropdownMenuItem(text = { Text(WidgetRefreshSchedule.label(value)) },
                    onClick = { onSelected(value); expanded = false })
            }
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
            .padding(vertical = 4.dp)
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
