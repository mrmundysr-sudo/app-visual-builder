package com.personal.appvisualbuilder.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/** Coordinates are normalized 0..1. Box: x,y = top-left, w,h = size. Point: x,y = center. */
data class CoordItem(
    val n: Int,
    val type: String, // "point" or "box"
    val label: String,
    val x: Double,
    val y: Double,
    val w: Double? = null,
    val h: Double? = null
) {
    val isBox get() = type == "box"
}

data class CoordsResult(
    val items: List<CoordItem>,
    val imageW: Int?,
    val imageH: Int?,
    val errors: List<String>,
    val warnings: List<String>
) {
    val ok get() = errors.isEmpty()
}

/** Parses and validates a coords@1 file. Never throws. */
object CoordsParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): CoordsResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val root: JsonElement = try {
            json.parseToJsonElement(text)
        } catch (e: Exception) {
            return CoordsResult(emptyList(), null, null, listOf("Not valid JSON: ${e.message?.take(80)}"), emptyList())
        }
        val obj = root as? JsonObject
            ?: return CoordsResult(emptyList(), null, null, listOf("Top level must be a JSON object"), emptyList())

        val schema = (obj["schema"] as? JsonPrimitive)?.contentOrNull
        if (schema != null && !schema.startsWith("app-visual-builder/coords@")) {
            warnings += "Unknown schema \"$schema\""
        }

        var imgW: Int? = null
        var imgH: Int? = null
        (obj["image_size"] as? JsonObject)?.let { size ->
            imgW = (size["w"] as? JsonPrimitive)?.intOrNull
            imgH = (size["h"] as? JsonPrimitive)?.intOrNull
        }

        val arr = obj["items"] as? JsonArray
        if (arr == null) {
            errors += "Missing \"items\" array"
            return CoordsResult(emptyList(), imgW, imgH, errors, warnings)
        }

        val items = mutableListOf<CoordItem>()
        val seen = mutableSetOf<Int>()
        arr.forEachIndexed { index, el ->
            val o = el as? JsonObject
            if (o == null) {
                errors += "Item #${index + 1} is not an object"
                return@forEachIndexed
            }
            val n = (o["n"] as? JsonPrimitive)?.intOrNull ?: (index + 1)
            val type = (o["type"] as? JsonPrimitive)?.contentOrNull
            val x = (o["x"] as? JsonPrimitive)?.doubleOrNull
            val y = (o["y"] as? JsonPrimitive)?.doubleOrNull
            val label = (o["label"] as? JsonPrimitive)?.contentOrNull ?: ""

            if (type != "point" && type != "box") {
                errors += "Item $n: type must be \"point\" or \"box\""
                return@forEachIndexed
            }
            if (x == null || y == null) {
                errors += "Item $n: missing x or y"
                return@forEachIndexed
            }
            var w: Double? = null
            var h: Double? = null
            if (type == "box") {
                w = (o["w"] as? JsonPrimitive)?.doubleOrNull
                h = (o["h"] as? JsonPrimitive)?.doubleOrNull
                if (w == null || h == null) {
                    errors += "Item $n: box needs w and h"
                    return@forEachIndexed
                }
            }
            if (!seen.add(n)) warnings += "Duplicate item number $n"
            if (label.isBlank()) warnings += "Item $n has no label"

            val outside = x !in 0.0..1.0 || y !in 0.0..1.0 ||
                (w != null && (x + w) > 1.0001) || (h != null && (y + h) > 1.0001) ||
                (w != null && w < 0) || (h != null && h < 0)
            if (outside) warnings += "Item $n is outside the image (values must be 0 to 1)"

            items += CoordItem(n, type, label, x, y, w, h)
        }
        return CoordsResult(items, imgW, imgH, errors, warnings)
    }

    /** Extra check against the clean image's pixel size. Returns a warning or null. */
    fun aspectWarning(result: CoordsResult, cleanW: Int, cleanH: Int): String? {
        val w = result.imageW ?: return null
        val h = result.imageH ?: return null
        if (h == 0 || cleanH == 0) return null
        val a = w.toDouble() / h
        val b = cleanW.toDouble() / cleanH
        return if (kotlin.math.abs(a - b) / b > 0.02)
            "Coords image size ${w}x$h doesn't match clean art ${cleanW}x$cleanH"
        else null
    }
}
