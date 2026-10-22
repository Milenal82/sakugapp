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
        if (!root.isJsonArray) throw DataFailure("帖子响应格式错误，未展示替代数据。")
        root.asJsonArray.map { element ->
            if (!element.isJsonObject) throw DataFailure("帖子数据格式错误。")
            val o = element.asJsonObject
            val id = o.integer("id")?.takeIf { it > 0 } ?: throw DataFailure("帖子缺少有效 ID。")
            Post(id,
                o.text("tags").orEmpty().split(Regex("\\s+")).filter { it.isNotEmpty() }
                    .distinct().map { Tag(it, categories[it] ?: TagCategory.UNKNOWN) },
                o.intValue("score"), safeUrl(o.text("preview_url")),
                o.intValue("preview_width")?.takeIf { it > 0 }, o.intValue("preview_height")?.takeIf { it > 0 },
                safeUrl(o.text("file_url")), o.text("file_ext"),
                o.intValue("width")?.takeIf { it > 0 }, o.intValue("height")?.takeIf { it > 0 },
                o.text("source"), o.text("rating"), o.integer("created_at"), o.text("status"), o.text("author"))
        }
    } catch (e: DataFailure) { throw e }
    catch (e: Exception) { throw DataFailure("无法解析帖子响应，未展示替代数据。", e) }

    // Format: type`canonical_name`alias` ...; never infer categories from names.
    fun tagSummary(body: String): Map<String, TagCategory> = try {
        val root = JsonParser.parseString(body)
        if (!root.isJsonObject) throw DataFailure("标签分类响应格式错误。")
        val data = root.asJsonObject.text("data") ?: throw DataFailure("标签分类响应缺少 data。")
        buildMap {
            data.trim().split(Regex("\\s+")).forEach { record ->
                val parts = record.split('`')
                val type = parts.firstOrNull()?.toIntOrNull()
                    ?: throw DataFailure("标签分类记录无效。")
                if (parts.size < 3 || parts[1].isBlank() || parts.last() != "")
                    throw DataFailure("标签分类记录不完整。")
                val category = when (type) {
                    1 -> TagCategory.ARTIST
                    3 -> TagCategory.COPYRIGHT
                    0, 4 -> TagCategory.OTHER
                    else -> TagCategory.UNKNOWN
                }
                parts.drop(1).filter { it.isNotEmpty() }.forEach { put(it, category) }
            }
        }
    } catch (e: DataFailure) { throw e }
    catch (e: Exception) { throw DataFailure("无法解析标签分类响应。", e) }
}
