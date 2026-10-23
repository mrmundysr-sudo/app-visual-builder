# App Visual Builder (starter project)

Personal Android tool that organizes, names, previews and packages the visual assets for a game so an AI coder can wire them in. Full requirements are in **SPEC.md**.

## Open and run

1. Install Android Studio (current stable).
2. File > Open > select this `AppVisualBuilder` folder. Let Gradle sync (uses Gradle 8.9 from `gradle/wrapper/gradle-wrapper.properties`).
3. Plug in your phone (USB debugging on) or start an emulator, then press Run.
4. To get a standalone APK: Build > Build Bundle(s) / APK(s) > Build APK(s).

The Gradle wrapper jar/scripts are not included. Android Studio doesn't need them. If you want `./gradlew`, run `gradle wrapper` once from a machine that has Gradle installed.

## What's implemented

| Area | Status |
|---|---|
| Project list (create, open, rename, duplicate, delete) | Working |
| New project wizard (name, page count, page names with suggestions) | Working |
| Setup area (add, rename, delete, move up/down) | Working. Up/down buttons instead of drag handles |
| Page editor: slot cards, notes, attach/replace/rename/remove | Working |
| Filename suggestions (editable) | Working (`model/Naming.kt`) |
| Archive on replace/remove, restore | Working (`data/ProjectRepository.kt`) |
| Coords parser + validation | Working (`model/Coords.kt`) |
| Coords preview overlay | Working, tap an item for its values |
| Flow preview (swipe/edge tap, overlay, markup toggle, end hints, wrap) | Working first version |
| Export checks, manifest.json, README.md, zip | Working (`export/Exporter.kt`) |
| Share sheet + Save to Downloads (Android 10+) | Working |

## Not done yet (good next tasks for the coder)

- Drag handles for page reorder (`ProjectRepository.reorder(projectId, from, to)` is ready).
- Unit tests for `CoordsParser`, `Naming`, and `Exporter.placements`.
- Save-to-Downloads on Android 8 and 9 (currently shows a "use Share" message).
- Large PSDs: attaching copies the file; a progress indicator would help for very large files.

## Code map

```
app/src/main/java/com/personal/appvisualbuilder/
  MainActivity.kt          single activity, screen switch, back handling
  model/Models.kt          Project, Page, AssetFile, Slot, archive types
  model/Naming.kt          slugs, filename suggestions, page IDs
  model/Coords.kt          coords@1 parser + validator
  data/ProjectRepository.kt  all storage, archive, restore
  export/Exporter.kt       checks, manifest, README, zip layout
  ui/                      Compose screens (Home, Project, Setup, PageEditor, CoordsPreview, FlowPreview)
sample/sample_coords.json  example file in the coords@1 format
```

## Golden rule

Pages have permanent IDs (`page_01`...) that never change or get reused. Files in the export use fixed slot names (`clean.png`, `markup_1.png`, `layers.psd`, `coords.json`). Art can be swapped any time without changing a path.
