package com.personal.appvisualbuilder.model

/** Filename suggestions and slugs. */
object Naming {

    /** "Game Over!" -> "game_over" */
    fun slug(name: String): String {
        val s = name.trim().lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
        return s.ifEmpty { "page" }
    }

    /** Suggested display name for a new attachment. Editable by the user. */
    fun suggest(page: Page, slot: Slot, originalName: String): String {
        val ext = extensionOf(originalName)
        val base = slug(page.name)
        val existing = page.files.map { it.displayName.lowercase() }.toSet()

        val stem = when (slot) {
            Slot.CLEAN -> "${base}_clean"
            Slot.MARKUP -> "${base}_markup"
            Slot.PSD -> "${base}_layers"
            Slot.COORDS -> "${base}_coords"
            Slot.OTHER -> "${base}_" + originalName.substringBeforeLast('.').let { slug(it) }
        }
        val finalExt = when (slot) {
            Slot.PSD -> "psd"
            Slot.COORDS -> "json"
            else -> ext.ifEmpty { "bin" }
        }
        var candidate = "$stem.$finalExt"
        var i = 2
        while (candidate.lowercase() in existing) {
            candidate = "${stem}_$i.$finalExt"
            i++
        }
        return candidate
    }

    /** Filename for a file filling a template need: the wanted name, with the extension of the file actually picked. */
    fun forNeed(need: NeedAsset, originalName: String): String {
        val needExt = extensionOf(need.name)
        val stem = if (needExt.isEmpty()) need.name else need.name.dropLast(needExt.length + 1)
        val ext = when (need.slot) {
            Slot.PSD -> "psd"
            Slot.COORDS -> "json"
            else -> extensionOf(originalName).ifEmpty { needExt }
        }
        return if (ext.isEmpty()) stem else "$stem.$ext"
    }

    /** Keeps a wanted filename safe to use as a zip path. */
    fun safeFile(name: String): String = name.replace(Regex("[^A-Za-z0-9._-]+"), "_")

    fun extensionOf(name: String): String =
        if (name.contains('.')) name.substringAfterLast('.').lowercase().filter { it.isLetterOrDigit() } else ""

    /** Next permanent page ID. */
    fun pageId(number: Int): String = "page_" + number.toString().padStart(2, '0')

    /** Folder name inside the zip: permanent ID + current name slug. */
    fun pageFolder(page: Page): String = "${page.id}_${slug(page.name)}"

    /** Page-name suggestions for the new-project wizard. */
    val defaultPageNames = listOf("Splash", "Menu", "Gameplay", "Win", "Loss", "Settings", "Pause")

    fun suggestedPageName(index: Int): String =
        defaultPageNames.getOrNull(index) ?: "Page ${index + 1}"
}
