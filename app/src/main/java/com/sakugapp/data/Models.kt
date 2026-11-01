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
    val fileSize: Long? = null,
    val durationMs:Long? = null,
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

data class PostPage(val posts: List<Post>, val rawCount: Int, val categoryWarning: String? = null,
    val classifications: Map<String, TagCategory> = emptyMap())

data class FeedState(
    val query: String = "",
    val posts: List<Post> = emptyList(),
    val nextPage: Int = 1,
    val loading: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
    val categoryWarning: String? = null,
    val autoLoadPaused: Boolean = false,
    val refreshing:Boolean=false,
    val refreshError:String?=null,
    val refreshRevision:Int=0,
    val discardedPosts:Int=0,
) {
    companion object { const val MAX_RETAINED_POSTS=600 }
    fun append(page: PostPage, pageSize: Int): FeedState {
        val merged=(posts+page.posts).distinctBy{it.id}
        return copy(
        // Also repair earlier cards when a classification request succeeds on a later page.
        posts = merged.takeLast(MAX_RETAINED_POSTS).map { post ->
            if(post.tags.any{tag->page.classifications[tag.name]?.let{it!=tag.category}==true})
                post.copy(tags=post.tags.map{tag->page.classifications[tag.name]?.takeIf{it!=tag.category}?.let{tag.copy(category=it)} ?: tag})
            else post
        },
        nextPage = nextPage + 1,
        discardedPosts=discardedPosts+(merged.size-MAX_RETAINED_POSTS).coerceAtLeast(0),
        loading = false,
        endReached = page.rawCount < pageSize,
        error = null,
        categoryWarning = page.categoryWarning,
        // A server returning the same full page must not cause an automatic request loop.
        // The explicit Load more action can still advance to the following page.
        autoLoadPaused = posts.isNotEmpty() && page.rawCount>0 && posts.mapTo(HashSet()){it.id}.let{ids->page.posts.all{it.id in ids}},
    )
    }
}

class DataFailure(message: String, cause: Throwable? = null) : Exception(message, cause)

fun httpError(code: Int): String = when (code) {
    401, 403 -> "Access denied (HTTP $code). Retry later or open the original site; access restrictions are not bypassed."
    421, 429 -> "Rate limited (HTTP $code). Wait before retrying."
    in 500..599 -> "The website is temporarily unavailable (HTTP $code)."
    else -> "Request failed (HTTP $code)."
}
