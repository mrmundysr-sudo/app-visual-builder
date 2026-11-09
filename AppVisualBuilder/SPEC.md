# App Visual Builder: Build Spec

Personal Android tool (Kotlin + Jetpack Compose) that organizes, names, previews and packages the visual assets for a game or app, so an AI coder can wire them in. It does not edit art. Other apps do that work; this one is the organizer and packager.

---

## 1. Core idea

- A **project** is one game/app. It has an ordered list of **pages** (splash, gameplay, win, loss, etc.).
- Each page has fixed **slots** you attach files into: Clean, Markup, PSD, Coords, Other.
- The app suggests filenames, lets you edit them, and tracks everything by permanent IDs.
- A full-screen **flow preview** lets you swipe through the clean art to feel the game flow.
- **Export** produces one zip: `manifest.json`, `README.md`, and a `pages/` folder with fixed slot filenames.

**Golden rule: swap without breaking anything.** Pages and slots have permanent IDs. The coder wires to slots, never to display names. Replacing art never changes a path in the export.

---

## 2. Decisions locked in

| Area | Decision |
|---|---|
| Platform | Native Android, Kotlin + Jetpack Compose |
| Theme | Dark only. Preview background is pure black |
| Home | Project list: create, open, duplicate, rename, delete |
| Storage | Saved inside the app (private storage); export via share sheet or Downloads |
| Page order | Order of setup list; add, rename, delete, drag to reorder in a Setup area |
| Attachments | Free-form per page, tagged by role (slot) |
| PSD | Store only. Generic icon, no parsing |
| Replacing a file | Overwrite slot, old file moves to project `archive/` (not exported); one-tap restore |
| Coords JSON | Validate + preview as dots/boxes over clean PNG |
| Coordinates | Normalized 0 to 1 fractions |
| Item data | Number + one short label only |
| Preview nav | Swipe or tap left/right edges, in page order |
| Preview orientation | Auto (follows phone), art scaled to fit, never cropped |
| Preview layer | Clean art; toggle button cycles through markup images |
| Preview controls | Hidden until center tap; fade after ~2.5 s |
| Preview ends | "End of flow" / "Start of flow" hint; second swipe wraps |
| Page screen | Slot cards + page notes field |
| Export layout | Fixed slot filenames inside `page_XX_name` folders |
| Export check | Warn, list problems, allow export anyway |
| Deliverable | This spec + starter project |

---

## 3. Screens

### 3.1 Project list (home)
- List of projects: name, page count, last modified, thumbnail (first page's clean art if present).
- FAB: **New project**.
- Per-project menu: Open, Duplicate, Rename, Delete (confirm).
- Duplicate copies all files and keeps page IDs.

### 3.2 New project wizard
1. App name.
2. Number of pages.
3. A name for each page (prefilled with suggestions: Splash, Menu, Gameplay, Win, Loss, Settings... then Page 7, Page 8).
4. Create. Pages get IDs `page_01`, `page_02`, ... in order.

### 3.3 Project home
- Header: project name, **Preview flow**, **Export**, **Setup**.
- Vertical list of pages in order: number, name, thumbnail, slot-fill chips (Clean / Markup / PSD / Coords), warning icon if a problem exists.
- Tap a page to open Page editor.

### 3.4 Setup area
- Rename project.
- Pages list with drag handles for reorder.
- Add page (gets the next unused ID, never reuses a deleted ID).
- Rename page.
- Delete page (confirm; its files move to `archive/`).
- Reordering and renaming never change a page ID.

### 3.5 Page editor
- Top: **Page notes** text box (instructions to the AI coder, e.g. "confetti plays on entry"). Saved automatically.
- Slot cards:
  - **Clean** (one file, PNG). Thumbnail, display name, Replace, Rename, Restore from archive.
  - **Markup** (multiple images). Numbered list, add/replace/rename/remove each.
  - **PSD** (one file).
  - **Coords** (one JSON). Shows validation status + **Preview mapping** button.
  - **Other** (multiple files, any type).
- Attaching: system file picker, then a **name dialog** with the suggested filename, editable.
- Replacing a filled slot: old file goes to `archive/`, new one takes the slot.

### 3.6 Coords preview
- Clean PNG with dots (points) and rectangles (boxes) drawn from the coords JSON.
- Each shows its number and label.
- Tap an item to highlight it and show its raw values.
- Warns on items outside 0..1.

### 3.7 Flow preview (full screen)
- Immersive mode (system bars hidden), pure black background.
- Shows the page's clean PNG, scaled to fit, any orientation.
- Left edge tap / swipe right = previous. Right edge tap / swipe left = next. Edge zones are ~20% of width each.
- Center tap toggles the overlay: page name, "3 of 7", markup toggle (cycles markup images, then back to clean), exit button. Fades after ~2.5 s.
- First/last page: brief "Start of flow" / "End of flow" message. Another swipe in that direction wraps.
- Page with no clean art shows a placeholder card with the page name.

### 3.8 Export
- Runs the checks (section 8) and shows a list.
- **Export anyway** or **Cancel**.
- Writes `<AppName>_visual_assets.zip` to a cache file, then offers: Share sheet and Save to Downloads.

---

## 4. Naming assistant

Suggested filenames (editable before save):

| Role | Suggestion |
|---|---|
| Clean | `<page>_clean.png` |
| Markup | `<page>_markup.png`, `<page>_markup_2.png`... |
| PSD | `<page>_layers.psd` |
| Coords | `<page>_coords.json` |
| Other | `<page>_<original name>` |

`<page>` is the page name lowercased, spaces to underscores, non-alphanumerics removed. Extensions come from the picked file. Duplicate names inside a page get `_2`, `_3`.

The name you choose is the **display name**. It shows in the app and is stored in the manifest. It is never used as the zip path.

---

## 5. Data model

```
Project
  id: String (uuid)
  name: String
  createdAt, modifiedAt: Long
  nextPageNumber: Int         // never reused
  pages: List<Page>           // list order = flow order

Page
  id: String                  // "page_01", permanent
  name: String
  notes: String
  files: List<AssetFile>

AssetFile
  id: String (uuid)
  slot: CLEAN | MARKUP | PSD | COORDS | OTHER
  displayName: String         // what you named it
  storedName: String          // internal file in project storage
  originalName: String
  mime: String?
  addedAt: Long
```

### On-disk layout (app private storage)

```
files/projects/<projectId>/
  project.json
  assets/<assetId>.<ext>
  archive/<assetId>.<ext>
  archive/archive.json         // list of archived entries for restore
```

### Export zip layout

```
<AppName>_visual_assets.zip
  manifest.json
  README.md
  pages/
    page_01_splash/
      clean.png
      markup_1.png
      markup_2.png
      layers.psd
      coords.json
      other_1.<ext>
    page_02_gameplay/
    ...
```

Folder name = permanent page ID + current page name slug. Slot filenames are fixed:
`clean.<ext>`, `markup_N.<ext>`, `layers.psd`, `coords.json`, `other_N.<ext>`.

Note: if you rename a page, the folder suffix changes but the ID prefix does not. The manifest always gives the exact path, and coders should key on `id`.

---

## 6. manifest.json

```json
{
  "schema": "app-visual-builder/manifest@1",
  "app_name": "My Game",
  "exported_at": "2026-10-16T22:51:00-04:00",
  "page_count": 3,
  "coordinate_space": "normalized_0_1",
  "pages": [
    {
      "id": "page_01",
      "index": 0,
      "name": "Splash",
      "folder": "pages/page_01_splash",
      "notes": "Fade in over 1s, then wait for tap.",
      "slots": {
        "clean": { "path": "pages/page_01_splash/clean.png", "display_name": "splash_clean.png" },
        "markup": [
          { "path": "pages/page_01_splash/markup_1.png", "display_name": "splash_markup.png" }
        ],
        "psd": { "path": "pages/page_01_splash/layers.psd", "display_name": "splash_layers.psd" },
        "coords": { "path": "pages/page_01_splash/coords.json", "display_name": "splash_coords.json" },
        "other": []
      },
      "image_size": { "w": 1080, "h": 1920 },
      "items": [
        { "n": 1, "type": "box",   "label": "play_button", "x": 0.25, "y": 0.70, "w": 0.50, "h": 0.10 },
        { "n": 2, "type": "point", "label": "logo_center", "x": 0.50, "y": 0.25 }
      ]
    }
  ]
}
```

Rules:
- Empty slots are `null` (single) or `[]` (multiple).
- `items` is copied from the coords file (if valid) so the coder gets everything in one place. If the coords file is invalid, `items` is `[]` and the problem is listed in README.
- `index` is flow order. `id` is permanent.

---

## 7. Coordinates JSON schema (`app-visual-builder/coords@1`)

The toolkit app will be updated to export this format.

```json
{
  "schema": "app-visual-builder/coords@1",
  "image_size": { "w": 1080, "h": 1920 },
  "items": [
    { "n": 1, "type": "box",   "label": "play_button", "x": 0.25, "y": 0.70, "w": 0.50, "h": 0.10 },
    { "n": 2, "type": "point", "label": "logo_center", "x": 0.50, "y": 0.25 }
  ]
}
```

| Field | Rule |
|---|---|
| `schema` | Optional but recommended. Must start with `app-visual-builder/coords@` if present |
| `image_size` | Optional. Pixel size of the image that was marked. Informational and used for aspect check |
| `items[].n` | Integer, the item number. Should be unique in the file |
| `items[].type` | `"point"` (x,y = center) or `"box"` (x,y = top-left, plus w,h) |
| `items[].label` | Short string |
| `x`, `y`, `w`, `h` | Numbers 0 to 1, fractions of image width/height |

Future-proofing: unknown fields are ignored and preserved. Optional `note` and `kind` fields can be added later without breaking old files.

Validation levels:
- **Error:** not valid JSON, no `items` array, item missing `type`/`x`/`y`, box missing `w`/`h`.
- **Warning:** value outside 0..1, duplicate `n`, empty label, `image_size` aspect ratio differs from the clean PNG by more than 2%.

Errors and warnings never block attaching. They show on the card and in the export check.

---

## 8. Export checks (warn, don't block)

Listed before export, per page:
- No clean art.
- Coords file is invalid JSON or fails the schema.
- Coords items outside the image (0..1).
- Coords aspect ratio doesn't match the clean PNG.
- Duplicate page names.
- Empty page name.
- Page has Markup but no Clean.

Project level:
- Zero pages.
- Duplicate item numbers within a page.

---

## 9. README.md (generated)

Contains:
1. Project name, export date, page count.
2. How to use: "Wire to page IDs and slots, not to filenames."
3. Table of pages in flow order with fill status.
4. Per page: notes, file paths, display names, item list (number, type, label, position).
5. A **Problems** section if the export check had warnings.
6. Coordinate convention reminder: normalized 0..1, box = top-left + size, point = center, origin top-left.

---

## 10. Swap rules

- Replace in slot: old file to `archive/`, new file takes the slot. IDs and export paths unchanged.
- Restore: pick an archived file for a slot; the current file (if any) is archived in turn.
- Archive is never exported.
- Delete page: files archived, page ID retired (never reused).
- Reorder/rename: only `index` and names change in the manifest; IDs stay.

---

## 11. Technical notes

- Min SDK 26, target current. Compose Material 3, dark color scheme.
- Single Activity, simple screen state in a ViewModel (no DI framework needed).
- Files copied into private storage via `ContentResolver` on attach (no persistent URI permissions to manage).
- Thumbnails and preview use Coil (`coil-compose`).
- JSON via `kotlinx.serialization`.
- Zip via `java.util.zip.ZipOutputStream`.
- Share via `FileProvider` + `ACTION_SEND`. Save to Downloads via `MediaStore.Downloads`.
- Preview uses `HorizontalPager` for swipe, with edge-tap zones layered above it.
- Immersive mode via `WindowInsetsControllerCompat`; orientation unlocked (`fullSensor` for the preview activity/screen).
- Reorder in Setup: simple up/down buttons in the starter; replace with drag handles when ready (noted in code).

---

## 12. Build order for the AI coder

1. Models + `ProjectRepository` (create/load/save, duplicate, delete).
2. Attach + naming + archive/restore.
3. Setup area (add, rename, reorder, delete).
4. Page editor with slot cards and notes.
5. Coords parser, validator, preview overlay.
6. Flow preview.
7. Export checks, manifest, README, zip, share/save.
8. Polish: drag-to-reorder, empty states, error toasts.

The starter project covers steps 1, 2, 3 (basic), 5, 7 in working code and steps 4 and 6 as functional first versions.

---

## 13. Template workflow (needs file)

The AI coder side writes a **needs file** (`app-visual-builder/needs@1`) listing everything it needs for a new app. The builder imports it and becomes a fill-in template; you fill it in, export, and hand the zip back.

Flow:
1. Download the needs file (`<app>_needs.json`). In the builder: **Import needs** (home screen). It shows page/file/region counts and asks before creating the project.
2. Each page shows a **Template** card: purpose, art size, PSD layer names (names only, never checked against the file), every requested file, and every region to place.
3. **Add** on a requested file opens the picker and names the file exactly as the template asks (no dialog). The wanted extension is replaced by the picked file's real extension.
4. **Place regions**: pick a region, drag a box (or tap a point) over the art. Works before the clean art exists, using the template's art size.
5. Skipping is always allowed. Empty items are flagged "still needed" on the page, the project list, the flow preview placeholder and the export check. Export never blocks.
6. **Update from needs file** (project screen) merges a newer needs file: pages match by `key`; nothing is removed, and files, notes, page names and placed regions are kept.

```json
{
  "schema": "app-visual-builder/needs@1",
  "app_name": "Sample Word Hunt",
  "pages": [
    {
      "key": "gameplay",
      "name": "Gameplay",
      "purpose": "Main puzzle screen.",
      "size": { "w": 1080, "h": 1920 },
      "notes": "Instructions copied into the page notes.",
      "assets": [
        { "key": "clean", "slot": "clean", "name": "gameplay_clean.png", "description": "...", "required": true },
        { "key": "pause_pressed", "slot": "other", "name": "pause_btn_pressed.png" }
      ],
      "layers": ["background", "grid_frame"],
      "regions": [
        { "label": "pause_btn", "type": "box", "action": "open the pause menu", "required": true }
      ]
    }
  ]
}
```

| Field | Rule |
|---|---|
| `schema` | Required, `app-visual-builder/needs@1` |
| `app_name` | Required |
| `pages[].key` | Stable ID for merging. Defaults to the name slug |
| `pages[].name` | Required |
| `assets[].slot` | `clean`, `markup`, `psd`, `coords` or `other`. Only one clean/psd/coords per page |
| `assets[].name` | Exact filename wanted |
| `assets[].key` | Stable ID. Defaults to the name stem |
| `assets[].required`, `regions[].required` | Default true |
| `regions[].type` | `box` (default) or `point` |
| `regions[].action` | What the touch does. Copied to the manifest |

Export effects:
- A file that fills a template need in the `markup` or `other` slot keeps the wanted filename in the zip (for example `pause_btn_pressed.png`), so its path never depends on what else is filled. `clean`, `layers.psd` and `coords.json` keep their fixed names. The manifest adds a `need` key to those entries.
- Manifest pages gain `purpose`, `layers`, `regions` (with `action`, `placed`), and `open_needs` (what is still empty). `items` comes from the coords file if valid, otherwise from placed regions; each item gets its region's `action`.
- Export checks add: required file still needed, required region not placed, clean art aspect differs from the template's size.
