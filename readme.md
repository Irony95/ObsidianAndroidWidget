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

### Comparison

| Original | Fork – Dark Mode | Fork – Light Mode
|---|---|---|
| ![Original](https://github.com/user-attachments/assets/1c6b17fd-90b8-457a-b349-18b7e7a6f608) | ![Fork - Dark Mode](https://github.com/user-attachments/assets/655a00b7-d889-4112-827c-30f6de25f882) | ![Fork - Light Mode](https://github.com/user-attachments/assets/48cef4d4-c0fc-4160-8178-117c420ac780)

### Improved Config Screen

<img src="https://github.com/user-attachments/assets/8199ce3c-1c36-46b7-b25d-1fec850f2a1a" width="285">

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
