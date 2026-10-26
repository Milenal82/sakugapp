// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import com.sakugapp.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RepositoryTest {
    private val server = MockWebServer()
    @Before fun start() { server.start() }
    @After fun stop() { server.shutdown() }
    @Test fun capturedPublicResponsesWorkThroughTheRealRepository() = runBlocking {
        server.enqueue(MockResponse().setBody(javaClass.getResource("/live/posts.json")!!.readText()))
        server.enqueue(MockResponse().setBody(javaClass.getResource("/live/tags.json")!!.readText()))
        val page = SakugaRepository(server.url("/")).page("rating:safe", 1)
        assertEquals(2, page.rawCount)
        assertEquals(33, page.posts.first().score)
        assertEquals(TagCategory.ARTIST, page.posts.first().tags.first { it.name == "mokmok" }.category)
        assertNull(page.categoryWarning)
    }
    @Test fun queryIsEncodedAndSuccessfulCategoriesAreCached() = runBlocking {
        server.enqueue(MockResponse().setBody("""[{"id":1,"tags":"test_artist"}]"""))
        server.enqueue(MockResponse().setBody("""{"data":"1`test_artist` "}"""))
        server.enqueue(MockResponse().setBody("""[{"id":2,"tags":"test_artist"}]"""))
        val repo = SakugaRepository(server.url("/"))
        val page = repo.page("a&b c", 1)
        assertEquals(TagCategory.ARTIST, page.posts.single().tags.single().category)
        val request = server.takeRequest()
        assertEquals("a&b c", request.requestUrl!!.queryParameter("tags"))
        assertEquals("1", request.requestUrl!!.queryParameter("page"))
        assertEquals("60", request.requestUrl!!.queryParameter("limit"))
        assertEquals("/tag/summary.json", server.takeRequest().path)
        repo.page("", 2)
        assertEquals("2", server.takeRequest().requestUrl!!.queryParameter("page"))
        assertEquals(3, server.requestCount)
    }
    @Test fun forbiddenDoesNotTryAlternativeUrlsOrCreatePosts(): Unit = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403))
        try { SakugaRepository(server.url("/")).page("", 1); fail("expected failure") }
        catch (e: DataFailure) { assertTrue(e.message!!.contains("403")) }
        assertEquals(1, server.requestCount)
    }
    @Test fun throttlingDoesNotAutomaticallyRetry(): Unit = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).addHeader("Retry-After", "60"))
        try { SakugaRepository(server.url("/")).page("", 1); fail("expected failure") }
        catch (e: DataFailure) { assertTrue(e.message!!.contains("429")) }
        assertEquals(1, server.requestCount)
    }
    @Test fun classificationFailurePreservesPostsWithUnknownCategories() = runBlocking {
        server.enqueue(MockResponse().setBody("""[{"id":1,"tags":"test_artist","score":8}]"""))
        server.enqueue(MockResponse().setResponseCode(503))
        val page = SakugaRepository(server.url("/")).page("", 1)
        assertNotNull(page.categoryWarning)
        assertEquals(8, page.posts.single().score)
        assertEquals(TagCategory.UNKNOWN, page.posts.single().tags.single().category)
    }
    @Test fun emptyResultDoesNotMakeUnnecessaryTagRequest() = runBlocking {
        server.enqueue(MockResponse().setBody("[]"))
        assertTrue(SakugaRepository(server.url("/")).page("", 1).posts.isEmpty())
        assertEquals(1, server.requestCount)
    }
    @Test fun htmlSuccessResponseIsStillADataError(): Unit = runBlocking {
        server.enqueue(MockResponse().setBody("<html>challenge</html>"))
        try { SakugaRepository(server.url("/")).page("", 1); fail("expected failure") }
        catch (e: DataFailure) { assertNotNull(e.message) }
        assertEquals(1, server.requestCount)
    }
}
