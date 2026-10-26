// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI

/** Protocol based on inspected Moebooru sources; live-site validation is documented separately. */
object PostParser {
    private fun JsonObject.text(key: String): String? = get(key)?.takeIf {
        it.isJsonPrimitive && it.asJsonPrimitive.isString
    }?.asString?.takeIf { it.isNotBlank() }
    private fun JsonObject.integer(key: String): Long? = get(key)?.takeIf {
        it.isJsonPrimitive && (it.asJsonPrimitive.isNumber || it.asJsonPrimitive.isString)
    }?.asString?.toLongOrNull()
    private fun JsonObject.intValue(key: String): Int? = integer(key)?.takeIf {
        it in Int.MIN_VALUE..Int.MAX_VALUE
    }?.toInt()

    fun safeUrl(value: String?): String? = value?.takeIf {
        runCatching {
            val uri = URI(it)
            uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
        }.getOrDefault(false)
    }

    fun posts(body: String, categories: Map<String, TagCategory> = emptyMap()): List<Post> = try {
        val root = JsonParser.parseString(body)
        if (!root.isJsonArray) throw DataFailure("Invalid post response; no substitute data is shown.")
        root.asJsonArray.map { element ->
            if (!element.isJsonObject) throw DataFailure("Invalid post data.")
            val o = element.asJsonObject
            val id = o.integer("id")?.takeIf { it > 0 } ?: throw DataFailure("Post has no valid ID.")
            Post(id,
                o.text("tags").orEmpty().split(Regex("\\s+")).filter { it.isNotEmpty() }
                    .distinct().map { Tag(it, categories[it] ?: TagCategory.UNKNOWN) },
                o.intValue("score"), safeUrl(o.text("preview_url")),
                o.intValue("preview_width")?.takeIf { it > 0 }, o.intValue("preview_height")?.takeIf { it > 0 },
                safeUrl(o.text("file_url")), o.text("file_ext"),
                o.intValue("width")?.takeIf { it > 0 }, o.intValue("height")?.takeIf { it > 0 },
                o.text("source"), o.text("rating"), o.integer("created_at"), o.text("status"), o.text("author"),
                o.integer("file_size")?.takeIf { it >= 0 })
        }
    } catch (e: DataFailure) { throw e }
    catch (e: Exception) { throw DataFailure("Unable to parse posts; no substitute data is shown.", e) }

    // Format: type`canonical_name`alias` ...; never infer categories from names.
    fun tagSummary(body: String): Map<String, TagCategory> = try {
        val root = JsonParser.parseString(body)
        if (!root.isJsonObject) throw DataFailure("Invalid tag classification response.")
        val data = root.asJsonObject.text("data") ?: throw DataFailure("Missing tag classification data.")
        buildMap {
            // A bad site record must not erase every valid classification. Split at record
            // starts, not within a name: the live summary contains a whitespace-bearing name.
            data.trim().split(Regex("\\s+(?=\\d+`)")).forEach { record ->
                val parts = record.split('`')
                val type = parts.firstOrNull()?.toIntOrNull()
                    ?: return@forEach
                val names = parts.drop(1).dropLast(1)
                if (parts.size < 3 || parts[1].isBlank() || parts.last() != "" ||
                    names.any { it.isBlank() || it.any(Char::isWhitespace) }) return@forEach
                val category = when (type) {
                    1 -> TagCategory.ARTIST
                    3 -> TagCategory.COPYRIGHT
                    0, 4 -> TagCategory.OTHER
                    else -> TagCategory.UNKNOWN
                }
                names.forEach { put(it, category) }
            }
        }.also { if (it.isEmpty()) throw DataFailure("No valid tag classifications were returned.") }
    } catch (e: DataFailure) { throw e }
    catch (e: Exception) { throw DataFailure("Unable to parse tag categories.", e) }
}
