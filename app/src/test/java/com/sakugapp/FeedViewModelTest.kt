// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import androidx.lifecycle.SavedStateHandle
import com.sakugapp.data.*
import com.sakugapp.ui.FeedViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }
    private fun page(id: Long) = PostPage(PostParser.posts("""[{"id":$id}]"""), SakugaRepository.PAGE_SIZE)
    @Test fun detailSwipesFollowResultOrderAndLocalFolderOrderWithoutRewritingTheFeed()=runTest(dispatcher){
        val posts=PostParser.posts(javaClass.getResource("/live/posts.json")!!.readText())
        val repo=object:PostRepository{override suspend fun page(query:String,page:Int)=PostPage(posts,posts.size)}
        val model=FeedViewModel(SavedStateHandle(),repo);advanceUntilIdle()
        model.open(posts[0]);assertEquals(posts[1],model.adjacent(1));assertNull(model.adjacent(-1))
        model.selectAdjacent(posts[1]);assertEquals(posts[0],model.adjacent(-1));assertNull(model.adjacent(1))
        model.openLocal(posts[1],posts.reversed());assertEquals(posts[0],model.adjacent(1));assertNull(model.adjacent(-1))
        assertEquals(posts,model.state.value.posts)
        model.open(posts[0]);assertEquals(posts[1],model.adjacent(1))
    }
    @Test fun durationFromAPreviousUrlCannotCorruptTheCurrentPost()=runTest(dispatcher){
        val posts=PostParser.posts(javaClass.getResource("/live/posts.json")!!.readText())
        val repo=object:PostRepository{override suspend fun page(query:String,page:Int)=PostPage(posts,posts.size)}
        val model=FeedViewModel(SavedStateHandle(),repo);advanceUntilIdle();model.open(posts[0])
        model.updateDuration(posts[0].id,"https://www.sakugabooru.com/other.mp4",1234)
        assertNull(model.selected.value!!.durationMs)
        model.updateDuration(posts[0].id,posts[0].fileUrl!!,1234)
        assertEquals(1234L,model.selected.value!!.durationMs)
        assertEquals(1234L,model.state.value.posts[0].durationMs)
    }
    @Test fun failedAppendKeepsPostsAndRetriesTheSamePage() = runTest(dispatcher) {
        var failSecond = true
        val requests = mutableListOf<Int>()
        val repo = object : PostRepository {
            override suspend fun page(query: String, page: Int): PostPage {
                requests += page
                if (page == 2 && failSecond) throw DataFailure("HTTP 503")
                return page(page.toLong())
            }
        }
        val model = FeedViewModel(SavedStateHandle(), repo)
        advanceUntilIdle(); model.loadMore(); advanceUntilIdle()
        assertEquals(listOf(1L), model.state.value.posts.map { it.id })
        assertEquals(2, model.state.value.nextPage); assertNotNull(model.state.value.error)
        failSecond = false; model.loadMore(); advanceUntilIdle()
        assertEquals(listOf(1, 2, 2), requests)
        assertEquals(listOf(1L, 2L), model.state.value.posts.map { it.id })
    }
    @Test fun duplicateLoadRequestsAreSuppressed() = runTest(dispatcher) {
        var calls = 0
        val gate = CompletableDeferred<Unit>()
        val repo = object : PostRepository {
            override suspend fun page(query: String, page: Int): PostPage { calls++; gate.await(); return page(1) }
        }
        val model = FeedViewModel(SavedStateHandle(), repo)
        model.loadMore(); runCurrent(); model.loadMore()
        assertEquals(1, calls); gate.complete(Unit); advanceUntilIdle()
    }
    @Test fun searchCancelsOldRequestAndNeverMixesResults() = runTest(dispatcher) {
        val repo = object : PostRepository {
            override suspend fun page(query: String, page: Int): PostPage {
                if (query.isEmpty()) delay(1000)
                return page(if (query.isEmpty()) 1 else 2)
            }
        }
        val saved = SavedStateHandle()
        val model = FeedViewModel(saved, repo)
        runCurrent(); model.search(" new_tag "); advanceUntilIdle()
        assertEquals("new_tag", model.state.value.query)
        assertEquals("new_tag", saved.get<String>("query"))
        assertEquals(listOf(2L), model.state.value.posts.map { it.id })
    }
    @Test fun returningFromDetailsDoesNotReloadOrClearSearchResults() = runTest(dispatcher) {
        var calls = 0
        val repo = object : PostRepository {
            override suspend fun page(query: String, page: Int): PostPage { calls++; return page(8) }
        }
        val model = FeedViewModel(SavedStateHandle(mapOf("query" to "saved_tag")), repo)
        advanceUntilIdle()
        val before = model.state.value
        model.open(before.posts.single()); model.close()
        assertEquals(before, model.state.value); assertEquals(1, calls)
        assertNull(model.selected.value)
    }
    @Test fun refreshKeepsTheQueryReplacesFirstPageDeduplicatesAndResetsPagination()=runTest(dispatcher){
        var refreshed=false
        val repo=object:PostRepository{override val pageSize=2;override suspend fun page(query:String,page:Int):PostPage{
            assertEquals("tag_a -tag_b order:score",query)
            return if(refreshed)PostPage(listOf(page(9).posts.single(),page(9).posts.single()),2)else PostPage(listOf(page(page.toLong()).posts.single()),2)
        }}
        val model=FeedViewModel(SavedStateHandle(mapOf("query" to "tag_a -tag_b order:score")),repo)
        advanceUntilIdle();model.loadMore();advanceUntilIdle();assertEquals(3,model.state.value.nextPage)
        refreshed=true;model.refresh();assertTrue(model.state.value.refreshing);advanceUntilIdle()
        assertEquals(listOf(9L),model.state.value.posts.map{it.id});assertEquals(2,model.state.value.nextPage)
        assertFalse(model.state.value.refreshing);assertFalse(model.state.value.endReached)
    }
    @Test fun refreshFailureKeepsOldRowsAndPageAndRetryFetchesFirstPage()=runTest(dispatcher){
        var fail=false;val pages=mutableListOf<Int>()
        val repo=object:PostRepository{override suspend fun page(query:String,page:Int):PostPage{pages+=page;if(fail)throw DataFailure("HTTP 503");return page(1)}}
        val model=FeedViewModel(SavedStateHandle(),repo);advanceUntilIdle();val original=model.state.value
        fail=true;model.refresh();advanceUntilIdle()
        assertEquals(original.posts,model.state.value.posts);assertEquals(original.nextPage,model.state.value.nextPage)
        assertNotNull(model.state.value.refreshError);assertFalse(model.state.value.refreshing)
        fail=false;model.refresh();advanceUntilIdle();assertNull(model.state.value.refreshError);assertEquals(listOf(1,1,1),pages)
    }
    @Test fun lateUncooperativeAppendCannotOverwriteRefreshAndLoadMoreIsBlockedDuringRefresh()=runTest(dispatcher){
        val stale=CompletableDeferred<Unit>();val fresh=CompletableDeferred<Unit>();var firstCalls=0;var calls=0
        val repo=object:PostRepository{override val pageSize=1;override suspend fun page(query:String,page:Int):PostPage{
            calls++
            return when{page==2->{withContext(NonCancellable){stale.await()};page(99)};firstCalls++>0->{fresh.await();page(7)};else->page(1)}
        }}
        val model=FeedViewModel(SavedStateHandle(),repo);runCurrent();model.loadMore();runCurrent()
        model.refresh();runCurrent();val before=calls;model.loadMore();model.refresh();runCurrent();assertEquals(before,calls)
        fresh.complete(Unit);runCurrent();stale.complete(Unit);advanceUntilIdle()
        assertEquals(listOf(7L),model.state.value.posts.map{it.id});assertEquals(2,model.state.value.nextPage)
    }
    @Test fun aSearchDuringUncooperativeRefreshOwnsAllSubsequentResults()=runTest(dispatcher){
        val gate=CompletableDeferred<Unit>();var calls=0
        val repo=object:PostRepository{override suspend fun page(query:String,page:Int):PostPage{
            if(query.isEmpty() && calls++>0){withContext(NonCancellable){gate.await()};return page(8)}
            return page(if(query.isEmpty())1 else 2)
        }}
        val model=FeedViewModel(SavedStateHandle(),repo);runCurrent();model.refresh();runCurrent();model.search("new");runCurrent();gate.complete(Unit);advanceUntilIdle()
        assertEquals("new",model.state.value.query);assertEquals(listOf(2L),model.state.value.posts.map{it.id});assertFalse(model.state.value.refreshing)
    }

}
