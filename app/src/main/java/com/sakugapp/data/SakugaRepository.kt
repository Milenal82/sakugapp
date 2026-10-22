// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface PostRepository {
    suspend fun page(query: String, page: Int): PostPage
}

class SakugaRepository(
    private val baseUrl: HttpUrl = "https://www.sakugabooru.com/".toHttpUrl(),
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS).retryOnConnectionFailure(false).build(),
) : PostRepository {
    companion object { const val PAGE_SIZE = 20 }
    private var categories: Map<String, TagCategory>? = null
    private val categoryMutex = Mutex()

    override suspend fun page(query: String, page: Int): PostPage {
        require(page > 0)
        val url = baseUrl.newBuilder().addPathSegment("post.json")
            .addQueryParameter("limit", PAGE_SIZE.toString()).addQueryParameter("page", page.toString())
            .addQueryParameter("tags", query).build()
        // Validate posts before making the optional classification request.
        val posts = PostParser.posts(get(url))
        if (posts.isEmpty()) return PostPage(emptyList(), 0)
        var warning: String? = null
        val types = try {
            categoryMutex.withLock {
                categories ?: PostParser.tagSummary(get(baseUrl.resolve("tag/summary.json")!!))
                    .also { categories = it }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            warning = "标签分类未获取成功，未核实的标签显示为中性色。${e.message.orEmpty()}"
            emptyMap()
        }
        return PostPage(posts.map { post -> post.copy(tags = post.tags.map {
            it.copy(category = types[it.name] ?: TagCategory.UNKNOWN)
        }) }, posts.size, warning)
    }

    private suspend fun get(url: HttpUrl): String = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(url).header("Accept", "application/json")
            .header("User-Agent", "Sakugapp/0.2 (+https://github.com/Milenal82/sakugapp)").build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(
                    DataFailure("网络连接失败，请检查网络后重试。", e))
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val text = response.use {
                        if (!it.isSuccessful) throw DataFailure(httpError(it.code))
                        val body = it.body ?: throw DataFailure("服务器返回空响应。")
                        // Bound responses, including chunked responses; never treat a truncated body as JSON.
                        val source = body.source()
                        if (source.request(8L * 1024 * 1024 + 1)) throw DataFailure("服务器响应过大。")
                        body.string()
                    }
                    continuation.resume(text)
                } catch (e: Exception) {
                    if (!continuation.isCancelled) continuation.resumeWithException(
                        if (e is DataFailure) e else DataFailure("读取网络响应失败。", e))
                }
            }
        })
    }
}
