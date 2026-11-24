// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import androidx.lifecycle.SavedStateHandle
import com.sakugapp.data.*
import com.sakugapp.ui.FeedViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class LatestPostsTest {
    private val dispatcher=StandardTestDispatcher()
    @Before fun setup(){Dispatchers.setMain(dispatcher)}
    @After fun cleanup(){Dispatchers.resetMain()}
    private fun page(id:Long)=PostPage(PostParser.posts("""[{"id":$id}]"""),1)

    @Test fun repeatedTapsCoalesceInitialAndActiveFirstPageRequests()=runTest(dispatcher){
        var gate=CompletableDeferred<Unit>();val requests=mutableListOf<Pair<String,Int>>()
        val repo=object:PostRepository{override suspend fun page(query:String,page:Int):PostPage{
            requests+=query to page;gate.await();return page(1)
        }}
        val model=FeedViewModel(SavedStateHandle(),repo)
        repeat(10){model.refreshLatest()};runCurrent();assertEquals(listOf("" to 1),requests)
        gate.complete(Unit);advanceUntilIdle();gate=CompletableDeferred()
        model.refreshLatest();repeat(10){model.refreshLatest()};runCurrent()
        assertEquals(listOf("" to 1,"" to 1),requests);assertTrue(model.state.value.refreshing)
        gate.complete(Unit);advanceUntilIdle()
    }
    @Test fun latestLeavesResultsKeepsBrowseRowsAndReplacesOnlyAfterSuccess()=runTest(dispatcher){
        var fresh=false;val gate=CompletableDeferred<Unit>();val requests=mutableListOf<Pair<String,Int>>()
        val repo=object:PostRepository{override val pageSize=1;override suspend fun page(query:String,page:Int):PostPage{
            requests+=query to page
            if(fresh && query.isBlank()){gate.await();return page(9)}
            return page(if(query.isBlank())page.toLong() else 99)
        }}
        val saved=SavedStateHandle();val model=FeedViewModel(saved,repo);advanceUntilIdle()
        model.loadMore();advanceUntilIdle();model.beginSearch("artist order:score");advanceUntilIdle()
        fresh=true;model.refreshLatest();runCurrent()
        assertEquals("",model.state.value.query);assertEquals(listOf(1L,2L),model.state.value.posts.map{it.id})
        assertTrue(model.state.value.refreshing);assertNull(model.resultSession.value);assertFalse(model.searchMode.value)
        assertEquals("" to 1,requests.last());assertEquals("",saved.get<String>("browseQuery"))
        gate.complete(Unit);advanceUntilIdle();assertEquals(listOf(9L),model.state.value.posts.map{it.id})
        assertEquals(2,model.state.value.nextPage)
    }
    @Test fun failedLatestKeepsOldRowsAndCanRetryWithoutResumingASearch()=runTest(dispatcher){
        var fail=false;val repo=object:PostRepository{override suspend fun page(query:String,page:Int):PostPage{
            if(fail)throw DataFailure("HTTP 503");return page(if(query.isBlank())1 else 99)
        }}
        val model=FeedViewModel(SavedStateHandle(),repo);advanceUntilIdle();val ordinary=model.state.value.posts
        model.beginSearch("animated");advanceUntilIdle();fail=true;model.refreshLatest();advanceUntilIdle()
        assertEquals(ordinary,model.state.value.posts);assertNotNull(model.state.value.refreshError)
        assertEquals("",model.state.value.query);fail=false;model.refreshLatest();advanceUntilIdle()
        assertNull(model.state.value.refreshError);assertEquals(ordinary,model.state.value.posts)
    }
    @Test fun latestRejectsUncooperativeAppendAndSearchResponses()=runTest(dispatcher){
        val gate=CompletableDeferred<Unit>();var latest=false
        val repo=object:PostRepository{override val pageSize=1;override suspend fun page(query:String,page:Int):PostPage{
            if(query.isNotBlank() || page==2){withContext(NonCancellable){gate.await()};return page(99)}
            return page(if(latest)9 else 1)
        }}
        val model=FeedViewModel(SavedStateHandle(),repo);runCurrent();model.loadMore();runCurrent()
        model.beginSearch("order:score");runCurrent();latest=true;model.refreshLatest();runCurrent()
        gate.complete(Unit);advanceUntilIdle()
        assertEquals("",model.state.value.query);assertEquals(listOf(9L),model.state.value.posts.map{it.id})
    }
    @Test fun ordinaryRestorationAndDetailReturnNeverInvokeLatestRefresh()=runTest(dispatcher){
        var count=0;val repo=object:PostRepository{override suspend fun page(query:String,page:Int):PostPage{count++;return page(1)}}
        val model=FeedViewModel(SavedStateHandle(),repo);advanceUntilIdle()
        model.beginSearch("animated");advanceUntilIdle();val result=model.state.value
        model.open(result.posts.first());val token=model.captureDetail();model.close();model.restoreDetail(1,token)
        assertEquals(result,model.state.value);assertEquals(2,count)
        model.close();model.restoreBrowse();advanceUntilIdle();assertEquals(2,count)
    }
}
