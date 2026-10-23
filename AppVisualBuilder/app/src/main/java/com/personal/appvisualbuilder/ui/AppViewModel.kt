package com.personal.appvisualbuilder.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.personal.appvisualbuilder.data.ProjectRepository
import com.personal.appvisualbuilder.export.Exporter
import com.personal.appvisualbuilder.model.ArchiveEntry
import com.personal.appvisualbuilder.model.Project
import com.personal.appvisualbuilder.model.Slot

sealed class Screen {
    data object Home : Screen()
    data class ProjectHome(val projectId: String) : Screen()
    data class Setup(val projectId: String) : Screen()
    data class PageEditor(val projectId: String, val pageId: String) : Screen()
    data class CoordsPreview(val projectId: String, val pageId: String) : Screen()
    data class FlowPreview(val projectId: String, val startIndex: Int = 0) : Screen()
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val repo = ProjectRepository(app)
    val exporter = Exporter(repo)

    var projects by mutableStateOf(repo.listProjects())
        private set

    /** The project currently open. Always re-read from disk after a change. */
    var current by mutableStateOf<Project?>(null)
        private set

    private val stack = mutableListOf<Screen>(Screen.Home)
    var screen by mutableStateOf<Screen>(Screen.Home)
        private set

    // ---------- navigation ----------

    fun go(next: Screen) {
        stack += next
        screen = next
        if (next is Screen.ProjectHome && current?.id != next.projectId) current = repo.load(next.projectId)
        if (next is Screen.Home) refreshList()
    }

    /** Returns false if already at the root (let the system handle back). */
    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        screen = stack.last()
        if (screen is Screen.Home) refreshList()
        return true
    }

    private fun refreshList() {
        projects = repo.listProjects()
    }

    private fun apply(updated: Project?) {
        if (updated != null) current = updated
        refreshList()
    }

    // ---------- projects ----------

    fun createProject(name: String, pageNames: List<String>) {
        val p = repo.create(name, pageNames)
        refreshList()
        current = p
        go(Screen.ProjectHome(p.id))
    }

    fun duplicateProject(id: String) { repo.duplicate(id); refreshList() }
    fun deleteProject(id: String) { repo.delete(id); if (current?.id == id) current = null; refreshList() }
    fun renameProject(id: String, name: String) { apply(repo.rename(id, name)) }

    // ---------- pages ----------

    fun setNotes(pageId: String, notes: String) { current?.let { apply(repo.setNotes(it.id, pageId, notes)) } }
    fun renamePage(pageId: String, name: String) { current?.let { apply(repo.renamePage(it.id, pageId, name)) } }
    fun addPage(name: String) { current?.let { apply(repo.addPage(it.id, name)) } }
    fun deletePage(pageId: String) { current?.let { apply(repo.deletePage(it.id, pageId)) } }
    fun movePage(pageId: String, dir: Int) { current?.let { apply(repo.movePage(it.id, pageId, dir)) } }

    // ---------- files ----------

    fun attach(pageId: String, slot: Slot, uri: Uri, displayName: String, originalName: String) {
        current?.let { apply(repo.attach(it.id, pageId, slot, uri, displayName, originalName)) }
    }

    fun replaceAsset(pageId: String, assetId: String, uri: Uri, displayName: String, originalName: String) {
        current?.let { apply(repo.replaceAsset(it.id, pageId, assetId, uri, displayName, originalName)) }
    }

    fun renameAsset(pageId: String, assetId: String, name: String) {
        current?.let { apply(repo.renameAsset(it.id, pageId, assetId, name)) }
    }

    fun removeAsset(pageId: String, assetId: String) {
        current?.let { apply(repo.removeAsset(it.id, pageId, assetId)) }
    }

    fun archivedFor(pageId: String, slot: Slot): List<ArchiveEntry> =
        current?.let { repo.archivedFor(it.id, pageId, slot) } ?: emptyList()

    fun restore(entry: ArchiveEntry) {
        current?.let { apply(repo.restore(it.id, entry)) }
    }
}
