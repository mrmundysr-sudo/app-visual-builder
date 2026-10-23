package com.personal.appvisualbuilder.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.personal.appvisualbuilder.model.ArchiveEntry
import com.personal.appvisualbuilder.model.ArchiveIndex
import com.personal.appvisualbuilder.model.AssetFile
import com.personal.appvisualbuilder.model.Naming
import com.personal.appvisualbuilder.model.Page
import com.personal.appvisualbuilder.model.Project
import com.personal.appvisualbuilder.model.Slot
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * All project storage lives in the app's private files dir:
 *
 * files/projects/<projectId>/project.json
 * files/projects/<projectId>/assets/<assetId>.<ext>
 * files/projects/<projectId>/archive/<assetId>.<ext>
 * files/projects/<projectId>/archive/archive.json
 */
class ProjectRepository(private val context: Context) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    private val root: File get() = File(context.filesDir, "projects").also { it.mkdirs() }

    private fun dir(projectId: String) = File(root, projectId)
    private fun assetsDir(projectId: String) = File(dir(projectId), "assets").also { it.mkdirs() }
    private fun archiveDir(projectId: String) = File(dir(projectId), "archive").also { it.mkdirs() }

    fun assetFile(projectId: String, asset: AssetFile) = File(assetsDir(projectId), asset.storedName)
    fun archivedFile(projectId: String, asset: AssetFile) = File(archiveDir(projectId), asset.storedName)

    // ---------- projects ----------

    fun listProjects(): List<Project> =
        (root.listFiles() ?: emptyArray())
            .mapNotNull { load(it.name) }
            .sortedByDescending { it.modifiedAt }

    fun load(projectId: String): Project? = runCatching {
        val f = File(dir(projectId), "project.json")
        if (f.exists()) json.decodeFromString<Project>(f.readText()) else null
    }.getOrNull()

    fun save(project: Project): Project {
        val updated = project.copy(modifiedAt = System.currentTimeMillis())
        dir(updated.id).mkdirs()
        File(dir(updated.id), "project.json").writeText(json.encodeToString(updated))
        return updated
    }

    fun create(name: String, pageNames: List<String>): Project {
        val now = System.currentTimeMillis()
        val pages = pageNames.mapIndexed { i, n -> Page(id = Naming.pageId(i + 1), name = n.ifBlank { "Page ${i + 1}" }) }
        return save(
            Project(
                id = UUID.randomUUID().toString(),
                name = name.ifBlank { "Untitled" },
                createdAt = now,
                modifiedAt = now,
                nextPageNumber = pages.size + 1,
                pages = pages
            )
        )
    }

    fun rename(projectId: String, newName: String): Project? =
        load(projectId)?.let { save(it.copy(name = newName.ifBlank { it.name })) }

    /** Copies everything. Page IDs and asset IDs stay the same inside the copy. */
    fun duplicate(projectId: String): Project? {
        val src = load(projectId) ?: return null
        val newId = UUID.randomUUID().toString()
        dir(projectId).copyRecursively(dir(newId), overwrite = true)
        return save(src.copy(id = newId, name = src.name + " copy", createdAt = System.currentTimeMillis()))
    }

    fun delete(projectId: String) {
        dir(projectId).deleteRecursively()
    }

    // ---------- pages ----------

    private fun edit(projectId: String, block: (Project) -> Project): Project? =
        load(projectId)?.let { save(block(it)) }

    fun setNotes(projectId: String, pageId: String, notes: String) = edit(projectId) { p ->
        p.copy(pages = p.pages.map { if (it.id == pageId) it.copy(notes = notes) else it })
    }

    fun renamePage(projectId: String, pageId: String, name: String) = edit(projectId) { p ->
        p.copy(pages = p.pages.map { if (it.id == pageId) it.copy(name = name.ifBlank { it.name }) else it })
    }

    fun addPage(projectId: String, name: String) = edit(projectId) { p ->
        val page = Page(id = Naming.pageId(p.nextPageNumber), name = name.ifBlank { "Page ${p.pages.size + 1}" })
        p.copy(pages = p.pages + page, nextPageNumber = p.nextPageNumber + 1)
    }

    /** Files are archived (not destroyed). The page ID is retired and never reused. */
    fun deletePage(projectId: String, pageId: String) = edit(projectId) { p ->
        val page = p.pages.firstOrNull { it.id == pageId } ?: return@edit p
        page.files.forEach { archiveAsset(projectId, pageId, it) }
        p.copy(pages = p.pages.filterNot { it.id == pageId })
    }

    /** direction: -1 = up, +1 = down. Only the list position changes, never the ID. */
    fun movePage(projectId: String, pageId: String, direction: Int) = edit(projectId) { p ->
        val i = p.pages.indexOfFirst { it.id == pageId }
        val j = i + direction
        if (i < 0 || j !in p.pages.indices) return@edit p
        val list = p.pages.toMutableList()
        list.add(j, list.removeAt(i))
        p.copy(pages = list)
    }

    /** For drag-to-reorder later: move page from one index to another. */
    fun reorder(projectId: String, from: Int, to: Int) = edit(projectId) { p ->
        if (from !in p.pages.indices || to !in p.pages.indices) return@edit p
        val list = p.pages.toMutableList()
        list.add(to, list.removeAt(from))
        p.copy(pages = list)
    }

    // ---------- files ----------

    fun queryName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0) return c.getString(i) ?: "file"
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "file"
    }

    /**
     * Attach a picked file. For single slots (Clean, PSD, Coords) an existing file is archived
     * first, so the slot is overwritten but the old file can be restored.
     */
    fun attach(projectId: String, pageId: String, slot: Slot, uri: Uri, displayName: String, originalName: String): Project? =
        edit(projectId) { p ->
            val page = p.pages.firstOrNull { it.id == pageId } ?: return@edit p
            if (!slot.multiple) page.filesIn(slot).forEach { archiveAsset(projectId, pageId, it) }

            val ext = Naming.extensionOf(displayName).ifEmpty { Naming.extensionOf(originalName) }
            val assetId = UUID.randomUUID().toString()
            val stored = if (ext.isEmpty()) assetId else "$assetId.$ext"
            context.contentResolver.openInputStream(uri)?.use { input ->
                File(assetsDir(projectId), stored).outputStream().use { input.copyTo(it) }
            }
            val asset = AssetFile(
                id = assetId, slot = slot, displayName = displayName, storedName = stored,
                originalName = originalName, extension = ext, addedAt = System.currentTimeMillis()
            )
            p.copy(pages = p.pages.map { pg ->
                if (pg.id != pageId) pg
                else pg.copy(files = pg.files.filterNot { !slot.multiple && it.slot == slot } + asset)
            })
        }

    /** Replace one specific file (used for Markup/Other where several files exist). */
    fun replaceAsset(projectId: String, pageId: String, oldAssetId: String, uri: Uri, displayName: String, originalName: String): Project? =
        edit(projectId) { p ->
            val page = p.pages.firstOrNull { it.id == pageId } ?: return@edit p
            val old = page.files.firstOrNull { it.id == oldAssetId } ?: return@edit p
            archiveAsset(projectId, pageId, old)

            val ext = Naming.extensionOf(displayName).ifEmpty { Naming.extensionOf(originalName) }
            val assetId = UUID.randomUUID().toString()
            val stored = if (ext.isEmpty()) assetId else "$assetId.$ext"
            context.contentResolver.openInputStream(uri)?.use { input ->
                File(assetsDir(projectId), stored).outputStream().use { input.copyTo(it) }
            }
            val asset = old.copy(id = assetId, displayName = displayName, storedName = stored,
                originalName = originalName, extension = ext, addedAt = System.currentTimeMillis())
            p.copy(pages = p.pages.map { pg ->
                if (pg.id != pageId) pg
                else pg.copy(files = pg.files.map { if (it.id == oldAssetId) asset else it })
            })
        }

    fun renameAsset(projectId: String, pageId: String, assetId: String, newName: String) = edit(projectId) { p ->
        p.copy(pages = p.pages.map { pg ->
            if (pg.id != pageId) pg
            else pg.copy(files = pg.files.map { if (it.id == assetId) it.copy(displayName = newName.ifBlank { it.displayName }) else it })
        })
    }

    /** Remove from the page, but keep a copy in the archive. */
    fun removeAsset(projectId: String, pageId: String, assetId: String) = edit(projectId) { p ->
        val asset = p.pages.firstOrNull { it.id == pageId }?.files?.firstOrNull { it.id == assetId } ?: return@edit p
        archiveAsset(projectId, pageId, asset)
        p.copy(pages = p.pages.map { pg ->
            if (pg.id != pageId) pg else pg.copy(files = pg.files.filterNot { it.id == assetId })
        })
    }

    // ---------- archive ----------

    private fun archiveIndexFile(projectId: String) = File(archiveDir(projectId), "archive.json")

    private fun readArchive(projectId: String): ArchiveIndex = runCatching {
        val f = archiveIndexFile(projectId)
        if (f.exists()) json.decodeFromString<ArchiveIndex>(f.readText()) else ArchiveIndex()
    }.getOrDefault(ArchiveIndex())

    private fun writeArchive(projectId: String, index: ArchiveIndex) {
        archiveIndexFile(projectId).writeText(json.encodeToString(index))
    }

    private fun archiveAsset(projectId: String, pageId: String, asset: AssetFile) {
        val src = assetFile(projectId, asset)
        if (src.exists()) src.copyTo(archivedFile(projectId, asset), overwrite = true)
        src.delete()
        val index = readArchive(projectId)
        writeArchive(projectId, ArchiveIndex(index.entries + ArchiveEntry(asset, pageId, System.currentTimeMillis())))
    }

    fun archivedFor(projectId: String, pageId: String, slot: Slot): List<ArchiveEntry> =
        readArchive(projectId).entries
            .filter { it.pageId == pageId && it.asset.slot == slot }
            .sortedByDescending { it.archivedAt }

    /** Bring an archived file back. If the slot is single and filled, the current file is archived in turn. */
    fun restore(projectId: String, entry: ArchiveEntry): Project? = edit(projectId) { p ->
        val page = p.pages.firstOrNull { it.id == entry.pageId } ?: return@edit p
        val slot = entry.asset.slot
        if (!slot.multiple) page.filesIn(slot).forEach { archiveAsset(projectId, page.id, it) }

        val src = archivedFile(projectId, entry.asset)
        if (src.exists()) src.copyTo(assetFile(projectId, entry.asset), overwrite = true)
        src.delete()
        val index = readArchive(projectId)
        writeArchive(projectId, ArchiveIndex(index.entries.filterNot { it.asset.id == entry.asset.id && it.archivedAt == entry.archivedAt }))

        p.copy(pages = p.pages.map { pg ->
            if (pg.id != page.id) pg
            else pg.copy(files = pg.files.filterNot { !slot.multiple && it.slot == slot } + entry.asset)
        })
    }
}
