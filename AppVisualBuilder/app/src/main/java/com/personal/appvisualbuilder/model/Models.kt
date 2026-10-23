package com.personal.appvisualbuilder.model

import kotlinx.serialization.Serializable

/** The fixed slots a page can hold. Slot names in the export zip never change. */
@Serializable
enum class Slot(val label: String, val multiple: Boolean) {
    CLEAN("Clean", false),
    MARKUP("Markup", true),
    PSD("PSD", false),
    COORDS("Coords", false),
    OTHER("Other", true)
}

@Serializable
data class AssetFile(
    val id: String,
    val slot: Slot,
    /** The name the user chose. Shown in the app and stored in the manifest. Never used as a zip path. */
    val displayName: String,
    /** File name inside the project's assets/ folder (assetId + extension). */
    val storedName: String,
    val originalName: String,
    val extension: String,
    val addedAt: Long
)

@Serializable
data class Page(
    /** Permanent ID like "page_01". Never changes, never reused. */
    val id: String,
    val name: String,
    val notes: String = "",
    val files: List<AssetFile> = emptyList()
) {
    fun filesIn(slot: Slot): List<AssetFile> = files.filter { it.slot == slot }
    fun clean(): AssetFile? = filesIn(Slot.CLEAN).firstOrNull()
}

@Serializable
data class Project(
    val id: String,
    val name: String,
    val createdAt: Long,
    val modifiedAt: Long,
    /** Next number used to mint a page ID. Only ever goes up. */
    val nextPageNumber: Int,
    /** List order is the flow order. */
    val pages: List<Page>
)

/** Entry in archive/archive.json so replaced files can be restored. */
@Serializable
data class ArchiveEntry(
    val asset: AssetFile,
    val pageId: String,
    val archivedAt: Long
)

@Serializable
data class ArchiveIndex(val entries: List<ArchiveEntry> = emptyList())
