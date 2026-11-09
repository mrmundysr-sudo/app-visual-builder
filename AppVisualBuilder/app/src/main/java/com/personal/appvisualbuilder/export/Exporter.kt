package com.personal.appvisualbuilder.export

import android.graphics.BitmapFactory
import com.personal.appvisualbuilder.data.ProjectRepository
import com.personal.appvisualbuilder.model.AssetFile
import com.personal.appvisualbuilder.model.CoordsParser
import com.personal.appvisualbuilder.model.CoordsResult
import com.personal.appvisualbuilder.model.Naming
import com.personal.appvisualbuilder.model.NeedRegion
import com.personal.appvisualbuilder.model.Page
import com.personal.appvisualbuilder.model.Project
import com.personal.appvisualbuilder.model.Slot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import java.io.File
import java.time.OffsetDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class Problem(val pageId: String?, val message: String)

/** Where one file ends up inside the zip. Fixed slot names, never the user's display name. */
data class ZipPlacement(val asset: AssetFile, val zipPath: String)

class Exporter(private val repo: ProjectRepository) {

    private val prettyJson = Json { prettyPrint = true }

    // ---------- zip layout ----------

    fun placements(page: Page): List<ZipPlacement> {
        val folder = "pages/${Naming.pageFolder(page)}"
        fun ext(a: AssetFile) = if (a.extension.isEmpty()) "" else ".${a.extension}"
        // Files that fill a template need keep the exact filename the template asked for, so their
        // path never depends on what else is filled. Free-form files keep the numbered names.
        fun keyed(a: AssetFile): String? {
            val need = page.need?.assets?.firstOrNull { it.key == a.needKey } ?: return null
            return Naming.safeFile(Naming.forNeed(need, "f${ext(a)}"))
        }
        val out = mutableListOf<ZipPlacement>()
        page.filesIn(Slot.CLEAN).firstOrNull()?.let { out += ZipPlacement(it, "$folder/clean${ext(it)}") }
        var m = 0
        page.filesIn(Slot.MARKUP).forEach { a ->
            val k = keyed(a)
            out += ZipPlacement(a, "$folder/" + (k ?: "markup_${++m}${ext(a)}"))
        }
        page.filesIn(Slot.PSD).firstOrNull()?.let { out += ZipPlacement(it, "$folder/layers.psd") }
        page.filesIn(Slot.COORDS).firstOrNull()?.let { out += ZipPlacement(it, "$folder/coords.json") }
        var o = 0
        page.filesIn(Slot.OTHER).forEach { a ->
            val k = keyed(a)
            out += ZipPlacement(a, "$folder/" + (k ?: "other_${++o}${ext(a)}"))
        }
        return out
    }

    // ---------- coords ----------

    fun readCoords(project: Project, page: Page): CoordsResult? {
        val asset = page.filesIn(Slot.COORDS).firstOrNull() ?: return null
        val file = repo.assetFile(project.id, asset)
        if (!file.exists()) return null
        return CoordsParser.parse(file.readText())
    }

    fun imageSize(project: Project, asset: AssetFile): Pair<Int, Int>? {
        val file = repo.assetFile(project.id, asset)
        if (!file.exists()) return null
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        return if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth to opts.outHeight else null
    }

    // ---------- template needs ----------

    /** Labels of the items in a valid coords file (counts as "placed" for template regions). */
    fun coordLabels(project: Project, page: Page): Set<String> =
        readCoords(project, page)?.takeIf { it.ok }?.items?.map { it.label }?.toSet().orEmpty()

    fun isPlaced(r: NeedRegion, coordLabels: Set<String>) = r.placed || r.label in coordLabels

    /** Human-readable list of required template items still empty. Empty list = page is complete. */
    fun openNeeds(project: Project, page: Page): List<String> {
        val need = page.need ?: return emptyList()
        val labels = coordLabels(project, page)
        val out = mutableListOf<String>()
        need.assets.filter { it.required && !it.isFilledIn(page) }.forEach { out += "file ${it.name}" }
        need.regions.filter { it.required && !isPlaced(it, labels) }.forEach { out += "region ${it.label}" }
        return out
    }

    // ---------- checks (warn, never block) ----------

    fun check(project: Project): List<Problem> {
        val problems = mutableListOf<Problem>()
        if (project.pages.isEmpty()) problems += Problem(null, "Project has no pages")

        val names = project.pages.groupBy { it.name.trim().lowercase() }
        names.filter { it.value.size > 1 }.forEach { (name, pages) ->
            problems += Problem(null, "Duplicate page name \"$name\" (${pages.joinToString { it.id }})")
        }

        project.pages.forEach { page ->
            val label = "${page.id} \"${page.name}\""
            if (page.name.isBlank()) problems += Problem(page.id, "$label has an empty name")
            val clean = page.clean()
            if (clean == null) {
                problems += Problem(page.id, "$label has no clean art")
                if (page.filesIn(Slot.MARKUP).isNotEmpty()) problems += Problem(page.id, "$label has markup but no clean art")
            }
            page.need?.let { need ->
                val labels = coordLabels(project, page)
                need.assets.filter { it.required && !it.isFilledIn(page) && !(it.slot == Slot.CLEAN) }
                    .forEach { problems += Problem(page.id, "$label still needed: ${it.name}") }
                need.regions.filter { it.required && !isPlaced(it, labels) }
                    .forEach { problems += Problem(page.id, "$label region not placed: ${it.label}") }
                val w = need.width
                val h = need.height
                if (clean != null && w != null && h != null) {
                    imageSize(project, clean)?.let { (cw, ch) ->
                        val a = w.toDouble() / h
                        val b = cw.toDouble() / ch
                        if (kotlin.math.abs(a - b) / b > 0.02) problems += Problem(page.id, "$label clean art is ${cw}x$ch but the template asks for ${w}x$h")
                    }
                }
            }
            val coords = readCoords(project, page)
            if (coords != null) {
                coords.errors.forEach { problems += Problem(page.id, "$label coords: $it") }
                coords.warnings.forEach { problems += Problem(page.id, "$label coords: $it") }
                if (clean != null) {
                    imageSize(project, clean)?.let { (w, h) ->
                        CoordsParser.aspectWarning(coords, w, h)?.let { problems += Problem(page.id, "$label coords: $it") }
                    }
                }
            }
        }
        return problems
    }

    // ---------- manifest ----------

    private fun slotEntry(project: Project, page: Page, p: ZipPlacement): JsonElement = buildJsonObject {
        put("path", p.zipPath)
        put("display_name", p.asset.displayName)
        p.asset.needKey?.let { put("need", it) }
    }

    fun buildManifest(project: Project): String {
        val root = buildJsonObject {
            put("schema", "app-visual-builder/manifest@1")
            put("app_name", project.name)
            put("exported_at", OffsetDateTime.now().toString())
            put("page_count", project.pages.size)
            put("coordinate_space", "normalized_0_1")
            put("pages", buildJsonArray {
                project.pages.forEachIndexed { index, page ->
                    val placed = placements(page)
                    fun one(slot: Slot): JsonElement =
                        placed.firstOrNull { it.asset.slot == slot }?.let { slotEntry(project, page, it) } ?: JsonNull
                    fun many(slot: Slot): JsonElement = buildJsonArray {
                        placed.filter { it.asset.slot == slot }.forEach { add(slotEntry(project, page, it)) }
                    }
                    val coords = readCoords(project, page)
                    add(buildJsonObject {
                        put("id", page.id)
                        put("index", index)
                        put("name", page.name)
                        put("folder", "pages/${Naming.pageFolder(page)}")
                        put("notes", page.notes)
                        put("slots", buildJsonObject {
                            put("clean", one(Slot.CLEAN))
                            put("markup", many(Slot.MARKUP))
                            put("psd", one(Slot.PSD))
                            put("coords", one(Slot.COORDS))
                            put("other", many(Slot.OTHER))
                        })
                        val w = coords?.imageW ?: page.need?.width
                        val h = coords?.imageH ?: page.need?.height
                        if (w != null && h != null) {
                            put("image_size", buildJsonObject { put("w", w); put("h", h) })
                        } else {
                            put("image_size", JsonNull)
                        }
                        val need = page.need
                        val regionByLabel = need?.regions?.associateBy { it.label }.orEmpty()
                        if (need != null) {
                            put("purpose", need.purpose)
                            put("layers", buildJsonArray { need.layers.forEach { add(it) } })
                            put("open_needs", buildJsonArray { openNeeds(project, page).forEach { add(it) } })
                            put("regions", buildJsonArray {
                                need.regions.forEach { r ->
                                    add(buildJsonObject {
                                        put("label", r.label)
                                        put("type", r.type)
                                        put("action", r.action)
                                        put("required", r.required)
                                        put("placed", r.placed)
                                    })
                                }
                            })
                        }
                        put("items", buildJsonArray {
                            if (coords != null && coords.ok) {
                                coords.items.forEach { item ->
                                    add(buildJsonObject {
                                        put("n", item.n)
                                        put("type", item.type)
                                        put("label", item.label)
                                        put("x", item.x)
                                        put("y", item.y)
                                        if (item.isBox) { put("w", item.w ?: 0.0); put("h", item.h ?: 0.0) }
                                        regionByLabel[item.label]?.action?.takeIf { it.isNotBlank() }?.let { put("action", it) }
                                    })
                                }
                            } else {
                                need?.regions?.filter { it.placed }?.forEachIndexed { i, r ->
                                    add(buildJsonObject {
                                        put("n", i + 1)
                                        put("type", r.type)
                                        put("label", r.label)
                                        put("x", r.x ?: 0.0)
                                        put("y", r.y ?: 0.0)
                                        if (r.isBox) { put("w", r.w ?: 0.0); put("h", r.h ?: 0.0) }
                                        if (r.action.isNotBlank()) put("action", r.action)
                                    })
                                }
                            }
                        })
                    })
                }
            })
        }
        return prettyJson.encodeToString(JsonElement.serializer(), root)
    }

    // ---------- README ----------

    fun buildReadme(project: Project, problems: List<Problem>): String = buildString {
        appendLine("# ${project.name}: visual assets")
        appendLine()
        appendLine("Exported ${OffsetDateTime.now()} | ${project.pages.size} pages")
        appendLine()
        appendLine("## How to use this package")
        appendLine()
        appendLine("- Wire the app to **page IDs and slots**, not to filenames. IDs (like `page_03`) never change.")
        appendLine("- Each page folder has fixed filenames: `clean.<ext>`, `markup_N.<ext>`, `layers.psd`, `coords.json`, `other_N.<ext>`.")
        appendLine("- `clean` is the real game artwork. `markup` images and `coords.json` show where things go.")
        appendLine("- `manifest.json` lists every page, slot, note and coordinate item in flow order.")
        appendLine("- Artwork must stay swappable: load images by slot path so new art can be dropped in without code changes.")
        appendLine()
        appendLine("## Coordinate convention")
        appendLine()
        appendLine("- Values are fractions of the image (0 to 1). Origin is the top-left corner.")
        appendLine("- `point`: x, y = center.")
        appendLine("- `box`: x, y = top-left corner, w, h = width and height.")
        appendLine()
        appendLine("## Pages (flow order)")
        appendLine()
        appendLine("| # | ID | Name | Clean | Markup | PSD | Coords |")
        appendLine("|---|----|------|-------|--------|-----|--------|")
        project.pages.forEachIndexed { i, p ->
            fun mark(s: Slot) = if (p.filesIn(s).isEmpty()) "-" else p.filesIn(s).size.toString()
            appendLine("| ${i + 1} | ${p.id} | ${p.name} | ${mark(Slot.CLEAN)} | ${mark(Slot.MARKUP)} | ${mark(Slot.PSD)} | ${mark(Slot.COORDS)} |")
        }
        appendLine()

        project.pages.forEachIndexed { i, page ->
            appendLine("### ${i + 1}. ${page.name} (`${page.id}`)")
            appendLine()
            if (page.notes.isNotBlank()) {
                appendLine("**Notes:** ${page.notes.trim()}")
                appendLine()
            }
            page.need?.let { need ->
                if (need.purpose.isNotBlank()) { appendLine("**Purpose:** ${need.purpose}"); appendLine() }
                if (need.layers.isNotEmpty()) { appendLine("**PSD layers:** ${need.layers.joinToString(", ")}"); appendLine() }
                val open = openNeeds(project, page)
                if (open.isNotEmpty()) { appendLine("**Still empty (not supplied yet):** ${open.joinToString("; ")}"); appendLine() }
                if (need.regions.isNotEmpty()) {
                    appendLine("Regions:")
                    need.regions.forEach { r ->
                        val pos = if (!r.placed) "not placed" else if (r.isBox) "x=${r.x}, y=${r.y}, w=${r.w}, h=${r.h}" else "x=${r.x}, y=${r.y}"
                        appendLine("- `${r.label}` (${r.type}) $pos${if (r.action.isNotBlank()) " | action: ${r.action}" else ""}")
                    }
                    appendLine()
                }
            }
            val placed = placements(page)
            if (placed.isEmpty()) appendLine("_No files attached._")
            placed.forEach { appendLine("- ${it.asset.slot.label}: `${it.zipPath}` (named \"${it.asset.displayName}\")") }
            val coords = readCoords(project, page)
            if (coords != null && coords.ok && coords.items.isNotEmpty()) {
                appendLine()
                appendLine("Items:")
                coords.items.forEach {
                    val pos = if (it.isBox) "x=${it.x}, y=${it.y}, w=${it.w}, h=${it.h}" else "x=${it.x}, y=${it.y}"
                    appendLine("- ${it.n}: `${it.label}` (${it.type}) $pos")
                }
            }
            appendLine()
        }

        if (problems.isNotEmpty()) {
            appendLine("## Problems found at export")
            appendLine()
            problems.forEach { appendLine("- ${it.message}") }
            appendLine()
        }
    }

    // ---------- zip ----------

    /** Writes the zip into cache/exports and returns it. */
    fun exportZip(project: Project, cacheDir: File): File {
        val outDir = File(cacheDir, "exports").also { it.mkdirs() }
        outDir.listFiles()?.forEach { it.delete() }
        val file = File(outDir, "${Naming.slug(project.name)}_visual_assets.zip")
        val problems = check(project)

        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            fun text(path: String, content: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            text("manifest.json", buildManifest(project))
            text("README.md", buildReadme(project, problems))
            project.pages.forEach { page ->
                placements(page).forEach { p ->
                    val src = repo.assetFile(project.id, p.asset)
                    if (src.exists()) {
                        zip.putNextEntry(ZipEntry(p.zipPath))
                        src.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
        }
        return file
    }
}
