# ObsidianAndroidWidgets

Android home-screen widget for displaying Obsidian notes on Android.

This fork focuses on making the original widget stable on modern Android as well as improvements for day-to-day use.

## Changes

- Fixes the `GlanceAppWidget` crash caused by exceeding the widget bitmap memory limit.
- Uses persisted Android document URIs instead of raw file paths to fit with modern permissioning.
- Improves compatibility with newer Android versions, including Android 14 intent handling.
- Refresh-only toolbar with feedback, and cleaner note title display.
- Light, dark, and custom colour theme options.
- Cached setup values so reconfiguration is faster when changing or re-naming the displayed note.
- Better markdown rendering support for tables, highlighting, task lists, code blocks, and more.
- Improved space-efficiency and overall layout.

## Screenshots

placeholder

## Install

1. Download the latest APK from the Releases page.
2. Allow installation from unknown sources if Android prompts you.
3. Install the APK on your device.
4. Add the `ObsidianAndroidWidgets` widget from your home screen widget picker.
5. Select your Obsidian vault **root folder** (not a sub-folder).
6. Select the note you want to display.
7. Choose a theme and complete setup.

## Usage Notes

- Tap the note body to open that note in Obsidian.
- Use the refresh button in the toolbar to reload the note.