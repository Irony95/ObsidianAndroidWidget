# ObsidianAndroidWidget

Android home screen widget for displaying Obsidian notes on Android.

This fork originally focused on making the original widget stable on modern Android and improving day-to-day usability. It has since expanded to support as much of Obsidian's Markdown formatting and functionality as reasonably possible.

## Changes

### Stability & Appearance

- Fixes the `GlanceAppWidget` crash caused by exceeding the widget bitmap memory limit.
- Uses persisted Android document URIs instead of raw file paths to work with modern Android permissions.
- Improves compatibility with newer Android versions, including Android 14 intent handling.
- Improved Nova launcher compatibility.
- Simplified toolbar with the note title, a refresh button with loading feedback, and improved handling of long titles.
- Light, dark, and custom themes, with separate text and background colours for the toolbar and note body. Quotes, links, and tags use your Obsidian accent colour or a custom colour.
- Improved space-efficiency and overall layout.
- Remembers your last-used vault, note, theme, colours, and refresh settings to make configuring additional widgets quicker.
- Choose how often notes refresh automatically, from 15 minutes to 24 hours, or refresh manually only.

### Markdown & Obsidian Features

- Better Markdown rendering support for tables, highlighting, lists, code blocks, and more.
- Tick and un-tick tasks directly in the widget, with changes saved to the note.
- Click external links, `[[wiki links]]`, headings, blocks, and tags to open them directly in your browser or Obsidian.
- Display embedded notes, headings, blocks, images, and PDF page previews directly in the widget.
- Syntax highlighting for code blocks, with a button to copy their contents.
- Support for footnotes, inline and display LaTeX maths, and Obsidian callouts, including icons, nesting, and collapsible sections.

<details>
<summary><strong>❗ Show all supported Markdown & Obsidian features (click to expand)</strong></summary>

- **Formatting:** Headings, bold, italics, strikethrough, blockquotes, and horizontal rules.
- **Basic HTML:** Supports basic HTML formatting alongside Markdown, including underlined, superscript, and subscript text.
- **Highlights:** Multiline `==highlights==`, including red, orange, yellow, green, blue, and purple highlights using [coloured emoji prefixes](https://obsidian.md/help/syntax#Highlight+colors).
- **Lists:** Bulleted, numbered, and nested lists, with consistent bullets and indentation guides.
- **Spacing and indentation:** Preserves line breaks and deliberate blank lines between paragraphs, headings, lists, tables, quotes, callouts, and code blocks. Supports four-space and tab indentation in prose.
- **Interactive tasks:** Tick and untick checkboxes directly in the widget, saving changes to the note without reloading the entire widget. Checks whether the note has changed before editing it, and uses the note's text colour for checkboxes. Strikes through tasks completed with `[x]` or `[X]`.
- **Tables:** Column alignment, wrapped text, alternating row shading, and formatting within cells, including inline code and highlights.
- **Web links:** Clickable plain URLs, email addresses, and Markdown links, including reference-style links.
- **Obsidian links:** Clickable `[[wiki links]]`, custom labels such as `[[Note|Label]]`, relative note links, and links to headings or block IDs.
- **Tags:** Styled tags which open the corresponding tag search in Obsidian.
- **Note embeds:** Read-only previews of full notes, individual headings, or blocks, including nested embeds. Full note embeds show the note title, with a button to open the original note or section.
- **Images:** Displays images from your vault or the web, using Markdown or Obsidian embeds, with support for custom image dimensions.
- **PDFs:** Displays static previews of the first page or a specified page using Obsidian's PDF embed syntax, e.g. `![[Document.pdf#page=2]]`.
- **Code:** Monospace inline code and fenced or indented code blocks, with separate backgrounds and padding. Language-specific syntax highlighting uses Atom One Dark or Light colours. Copy a code block using its copy icon or, when a language is specified, its language label.
- **LaTeX maths:** Renders mathematical notation inline with `$...$` or in centred blocks with `$$...$$`.
- **Footnotes:** Numbered, named, and inline footnotes, with superscript references and definitions collected at the end of the note.
- **Callouts:** Coloured Obsidian callouts with type-specific icons, custom titles, nesting, and controls to collapse or expand foldable sections.
- **Obsidian metadata:** Hides `%%comments%%`, frontmatter, and block IDs from the rendered note, while retaining block IDs as link targets.
- **Other attachments and web embeds:** Audio, video, Canvas, YouTube, X / Twitter, and webpage embeds open as links. Unavailable attachments also fall back to links.
</details>

## Screenshots

### Widget Comparison

| Original | Fork – Dark Mode | Fork – Light Mode |
|---|---|---|
| ![Original](https://github.com/user-attachments/assets/e9e916b4-a4ae-4247-8519-d42e2803f69b) | ![Fork – Dark Mode](https://github.com/user-attachments/assets/17528220-7712-4689-b9d1-fa5ae657f03d) | ![Fork – Light Mode](https://github.com/user-attachments/assets/bc325ee3-d1e5-43ec-9d54-b98cefc950c8) |

### Config Comparison

<table>
  <tr>
    <th>Original</th>
    <th>Fork</th>
  </tr>
  <tr>
    <td valign="top">
      <img src="https://github.com/user-attachments/assets/99d62d62-d5fd-48ec-b379-ebbef20d8654" width="280">
    </td>
    <td valign="top">
      <img src="https://github.com/user-attachments/assets/8570fb1d-8e2f-496a-89cb-66499a23cf11" width="280">
    </td>
  </tr>
</table>

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