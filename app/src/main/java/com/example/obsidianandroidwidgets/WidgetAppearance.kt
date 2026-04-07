package com.example.obsidianandroidwidgets

data class WidgetColorPalette(
    val toolbarBackgroundHex: String,
    val toolbarTextHex: String,
    val bodyBackgroundHex: String,
    val bodyTextHex: String,
)

enum class WidgetAppearanceMode(
    val storageValue: String,
    val label: String,
) {
    Light("light", "Light"),
    Dark("dark", "Dark"),
    Custom("custom", "Custom");

    companion object {
        fun fromStorage(value: String?): WidgetAppearanceMode {
            return values().firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: Dark
        }
    }
}

val LIGHT_WIDGET_PALETTE = WidgetColorPalette(
    toolbarBackgroundHex = "#F0F0F0",
    toolbarTextHex = "#000000",
    bodyBackgroundHex = "#FFFFFF",
    bodyTextHex = "#000000",
)

val DARK_WIDGET_PALETTE = WidgetColorPalette(
    toolbarBackgroundHex = "#21242B",
    toolbarTextHex = "#DCDDDE",
    bodyBackgroundHex = "#282B34",
    bodyTextHex = "#DCDDDE",
)

fun WidgetAppearanceMode.resolvePalette(customPalette: WidgetColorPalette): WidgetColorPalette {
    return when (this) {
        WidgetAppearanceMode.Light -> LIGHT_WIDGET_PALETTE
        WidgetAppearanceMode.Dark -> DARK_WIDGET_PALETTE
        WidgetAppearanceMode.Custom -> customPalette
    }
}
