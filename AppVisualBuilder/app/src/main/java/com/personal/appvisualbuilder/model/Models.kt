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
    val addedAt: Long,
    /** Which template need (NeedAsset.key) this file fulfils. Null for free-form files. */
    val needKey: String? = null
)

/** One file the template asks for. [name] is the exact filename wanted; filling it names the file automatically. */
@Serializable
data class NeedAsset(
    val key: String,
    val slot: Slot,
    val name: String,
    val description: String = "",
    val required: Boolean = true
) {
    /** Filled when a file points at this need, or (for single slots) anything sits in the slot. */
    fun isFilledIn(page: Page): Boolean =
        page.files.any { it.needKey == key } || (!slot.multiple && page.filesIn(slot).isNotEmpty())
}

/** A touch region the template asks for. Position is filled in by the user on the Place regions screen. */
@Serializable
data class NeedRegion(
    val label: String,
    val type: String = "box",
    val action: String = "",
    val required: Boolean = true,
    val x: Double? = null,
    val y: Double? = null,
    val w: Double? = null,
    val h: Double? = null
) {
    val isBox get() = type == "box"
    val placed get() = x != null && y != null && (!isBox || (w != null && h != null))
}

/** The template for one page, written by the AI coder side and imported from a needs file. */
@Serializable
data class PageNeed(
    /** Stable key from the needs file, used to match on re-import. */
    val key: String,
    val purpose: String = "",
    val width: Int? = null,
    val height: Int? = null,
    val assets: List<NeedAsset> = emptyList(),
    /** Layer names to use in the PSD. Names only, never checked against the file. */
    val layers: List<String> = emptyList(),
    val regions: List<NeedRegion> = emptyList()
)

@Serializable
data class Page(
    /** Permanent ID like "page_01". Never changes, never reused. */
    val id: String,
    val name: String,
    val notes: String = "",
    val files: List<AssetFile> = emptyList(),
    /** Null for pages not created from a needs file. */
    val need: PageNeed? = null
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
