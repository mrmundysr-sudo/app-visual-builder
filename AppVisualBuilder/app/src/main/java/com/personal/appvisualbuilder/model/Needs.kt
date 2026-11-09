package com.personal.appvisualbuilder.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** One page of a needs file: the name, notes and the template for that page. */
data class NeedsPage(val name: String, val notes: String, val need: PageNeed)

data class NeedsFile(val appName: String, val pages: List<NeedsPage>)

data class NeedsResult(val file: NeedsFile?, val errors: List<String>, val warnings: List<String>) {
    val ok get() = file != null && errors.isEmpty()
}

/**
 * Parses and validates an `app-visual-builder/needs@1` file. Never throws.
 * Unknown fields are ignored so the format can grow without breaking old apps.
 */
object NeedsParser {
    private val json = Json { ignoreUnknownKeys = true }

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.int(k: String) = (this[k] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.bool(k: String) = (this[k] as? JsonPrimitive)?.booleanOrNull

    fun parse(text: String): NeedsResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val root: JsonElement = try {
            json.parseToJsonElement(text)
        } catch (e: Exception) {
            return NeedsResult(null, listOf("Not valid JSON: ${e.message?.take(80)}"), emptyList())
        }
        val obj = root as? JsonObject
            ?: return NeedsResult(null, listOf("Top level must be a JSON object"), emptyList())

        val schema = obj.str("schema")
        if (schema == null || !schema.startsWith("app-visual-builder/needs@")) {
            errors += "Missing or unknown schema (expected \"app-visual-builder/needs@1\")"
        }
        val appName = obj.str("app_name")?.trim().orEmpty()
        if (appName.isEmpty()) errors += "Missing app_name"

        val arr = obj["pages"] as? JsonArray
        if (arr == null || arr.isEmpty()) {
            errors += "Missing or empty \"pages\" list"
            return NeedsResult(null, errors, warnings)
        }

        val pages = mutableListOf<NeedsPage>()
        val keys = mutableSetOf<String>()
        arr.forEachIndexed { pi, el ->
            val po = el as? JsonObject
            if (po == null) { errors += "Page ${pi + 1} is not an object"; return@forEachIndexed }
            val name = po.str("name")?.trim().orEmpty()
            if (name.isEmpty()) { errors += "Page ${pi + 1} has no name"; return@forEachIndexed }
            val key = (po.str("key")?.trim()?.takeIf { it.isNotEmpty() } ?: Naming.slug(name))
            if (!keys.add(key)) { errors += "Page \"$name\": duplicate key \"$key\""; return@forEachIndexed }
            val label = "Page \"$name\""

            val size = po["size"] as? JsonObject
            val width = size?.int("w")
            val height = size?.int("h")

            // assets
            val assets = mutableListOf<NeedAsset>()
            val assetKeys = mutableSetOf<String>()
            (po["assets"] as? JsonArray)?.forEachIndexed { ai, ael ->
                val ao = ael as? JsonObject
                if (ao == null) { errors += "$label asset ${ai + 1} is not an object"; return@forEachIndexed }
                val slot = when (ao.str("slot")?.lowercase()) {
                    "clean" -> Slot.CLEAN
                    "markup" -> Slot.MARKUP
                    "psd" -> Slot.PSD
                    "coords" -> Slot.COORDS
                    "other" -> Slot.OTHER
                    else -> null
                }
                val fname = ao.str("name")?.trim().orEmpty()
                if (slot == null) { errors += "$label asset ${ai + 1}: slot must be clean, markup, psd, coords or other"; return@forEachIndexed }
                if (fname.isEmpty()) { errors += "$label asset ${ai + 1}: missing name"; return@forEachIndexed }
                val akey = ao.str("key")?.trim()?.takeIf { it.isNotEmpty() } ?: Naming.slug(fname.substringBeforeLast('.'))
                if (!assetKeys.add(akey)) { warnings += "$label: duplicate asset key \"$akey\" ignored"; return@forEachIndexed }
                if (!slot.multiple && assets.any { it.slot == slot }) {
                    warnings += "$label: only one ${slot.label} file is allowed; \"$fname\" ignored"
                    return@forEachIndexed
                }
                assets += NeedAsset(akey, slot, fname, ao.str("description").orEmpty(), ao.bool("required") ?: true)
            }

            // layers (names only)
            val layers = (po["layers"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { s -> s.isNotEmpty() } }.orEmpty()

            // regions
            val regions = mutableListOf<NeedRegion>()
            (po["regions"] as? JsonArray)?.forEachIndexed { ri, rel ->
                val ro = rel as? JsonObject
                if (ro == null) { errors += "$label region ${ri + 1} is not an object"; return@forEachIndexed }
                val rlabel = ro.str("label")?.trim().orEmpty()
                if (rlabel.isEmpty()) { errors += "$label region ${ri + 1}: missing label"; return@forEachIndexed }
                val type = ro.str("type")?.lowercase() ?: "box"
                if (type != "box" && type != "point") { errors += "$label region \"$rlabel\": type must be box or point"; return@forEachIndexed }
                if (regions.any { it.label == rlabel }) { warnings += "$label: duplicate region \"$rlabel\" ignored"; return@forEachIndexed }
                regions += NeedRegion(rlabel, type, ro.str("action").orEmpty(), ro.bool("required") ?: true)
            }

            pages += NeedsPage(
                name = name,
                notes = po.str("notes").orEmpty(),
                need = PageNeed(key, po.str("purpose").orEmpty(), width, height, assets, layers, regions)
            )
        }

        if (errors.isNotEmpty()) return NeedsResult(null, errors, warnings)
        return NeedsResult(NeedsFile(appName, pages), emptyList(), warnings)
    }
}
