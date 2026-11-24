// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.sakugapp.data.*
import com.sakugapp.ui.FeedViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors

@OptIn(ExperimentalCoroutinesApi::class)
class LatestPostsHttpTest {
    private lateinit var main:ExecutorCoroutineDispatcher
    @Before fun setup(){main=Executors.newSingleThreadExecutor().asCoroutineDispatcher();Dispatchers.setMain(main)}
    @After fun cleanup(){Dispatchers.resetMain();main.close()}
    @Test fun latestRequestsTheActualDefaultFirstPageAndFailureRetainsTheCapturedPosts():Unit=runBlocking{
        MockWebServer().use{server->
            val body=javaClass.getResource("/live/posts.json")!!.readText()
            server.enqueue(MockResponse().setBody(body))
            server.enqueue(MockResponse().setBody(javaClass.getResource("/live/tags.json")!!.readText()))
            val model=withContext(main){FeedViewModel(SavedStateHandle(),SakugaRepository(server.url("/").newBuilder().host("127.0.0.1").build()))}
            val store=ViewModelStore().apply{put("feed",model)}
            try{
                val initial=withTimeout(10000){model.state.first{!it.loading}}
                assertEquals(2,initial.posts.size);server.takeRequest();server.takeRequest()
                server.enqueue(MockResponse().setBody(body).setBodyDelay(750,TimeUnit.MILLISECONDS))
                withContext(main){model.refreshLatest()}
                val request=withContext(Dispatchers.IO){server.takeRequest(5,TimeUnit.SECONDS)}!!
                assertEquals("/post.json",request.requestUrl!!.encodedPath)
                assertEquals("",request.requestUrl!!.queryParameter("tags"))
                assertEquals("1",request.requestUrl!!.queryParameter("page"))
                withContext(main){repeat(8){model.refreshLatest()}};assertEquals(3,server.requestCount)
                withTimeout(10000){model.state.first{!it.refreshing}}
                server.enqueue(MockResponse().setResponseCode(503));withContext(main){model.refreshLatest()}
                val failed=withTimeout(10000){model.state.first{!it.refreshing}}
                assertEquals(initial.posts,failed.posts);assertNotNull(failed.refreshError)
                assertEquals("1",server.takeRequest().requestUrl!!.queryParameter("page"));assertEquals(4,server.requestCount)
            }finally{withContext(main){store.clear()}}
        }
    }
}
