// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import java.net.URI

enum class TagCategory { COPYRIGHT, ARTIST, OTHER, UNKNOWN }
data class Tag(val name: String, val category: TagCategory)
data class Post(
    val id: Long,
    val tags: List<Tag>,
    val score: Int?,
    val previewUrl: String?,
    val previewWidth: Int?,
    val previewHeight: Int?,
    val fileUrl: String?,
    val fileExt: String?,
    val width: Int?,
    val height: Int?,
    val source: String?,
    val rating: String?,
    val createdAt: Long?,
    val status: String?,
    val uploader: String? = null,
) {
    val pageUrl: String get() = "https://www.sakugabooru.com/post/show/$id"
    val isVideo: Boolean get() = fileUrl != null &&
        (fileExt ?: runCatching { URI(fileUrl).path.substringAfterLast('.') }.getOrNull())
            ?.lowercase() in setOf("mp4", "webm", "mkv")
    val aspectRatio: Float? get() {
        val w = previewWidth ?: width
        val h = previewHeight ?: height
        return if (w != null && h != null && w > 0 && h > 0) w.toFloat() / h else null
    }
}

data class PostPage(val posts: List<Post>, val rawCount: Int, val categoryWarning: String? = null)

data class FeedState(
    val query: String = "",
    val posts: List<Post> = emptyList(),
    val nextPage: Int = 1,
    val loading: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
    val categoryWarning: String? = null,
) {
    fun append(page: PostPage, pageSize: Int): FeedState = copy(
        posts = (posts + page.posts).distinctBy { it.id },
        nextPage = nextPage + 1,
        loading = false,
        endReached = page.rawCount < pageSize,
        error = null,
        categoryWarning = page.categoryWarning,
    )
}

class DataFailure(message: String, cause: Throwable? = null) : Exception(message, cause)

fun httpError(code: Int): String = when (code) {
    401, 403 -> "网站拒绝访问（HTTP $code）。请稍后重试或打开原站；不会绕过访问限制。"
    421, 429 -> "网站请求频率受限（HTTP $code）。请稍后手动重试。"
    in 500..599 -> "网站服务暂不可用（HTTP $code）。"
    else -> "请求失败（HTTP $code）。"
}
