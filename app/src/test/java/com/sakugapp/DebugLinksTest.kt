// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import androidx.lifecycle.SavedStateHandle
import com.sakugapp.data.*
import com.sakugapp.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class DebugLinksTest {
    private val dispatcher=StandardTestDispatcher()
    @Before fun setup(){Dispatchers.setMain(dispatcher)}
    @After fun cleanup(){Dispatchers.resetMain()}
    private fun post(id:Long=1,source:String="episode 12",tags:List<Tag> = listOf(Tag("artist_a",TagCategory.ARTIST)))=
        PostParser.posts("""[{"id":$id,"source":"$source"}]""").single().copy(tags=tags)
    @Test fun everyColumnUsesExactZeroThroughTenSpacingWithoutAHighDensityOverride(){
        for(columns in listOf(1,2,3,4,6,8,12))for(value in 0..10){
            assertTrue(columns>0);assertEquals(value.toFloat(),GridSpacingPolicy.spacing(value.toFloat()),0f)
        }
        assertEquals(0f,GridSpacingPolicy.spacing(-1f),0f);assertEquals(10f,GridSpacingPolicy.spacing(11f),0f)
        assertEquals(8f,GridSpacingPolicy.spacing(Float.NaN),0f)
    }
    @Test fun artistCandidatesDeduplicatePostsAndTagsAndNeverGuessUnknownTypes(){
        val a=Tag("artist_a",TagCategory.ARTIST);val unknown=Tag("unknown",TagCategory.UNKNOWN)
        val posts=listOf(post(tags=listOf(a,a,unknown)),post(),post(2,tags=listOf(a,Tag("show",TagCategory.COPYRIGHT))))
        val candidates=ArtistLinks.candidates(posts)
        assertEquals(listOf("artist_a"),candidates.map{it.tag.name});assertEquals(2,candidates.single().count)
        assertEquals("show artist_a",ArtistLinks.query("show",a))
        assertThrows(IllegalArgumentException::class.java){ArtistLinks.query("show",unknown)}
    }
    @Test fun episodeFilterKeepsParsedEpisodeInsteadOfTrustingLooseSourceSearch(){
        val artist=Tag("artist_a",TagCategory.ARTIST)
        val posts=listOf(post(1,"#012"),post(2,"#13"),post(3,"https://example.org/12"),post(4,"episode 12",listOf(Tag("artist_a",TagCategory.UNKNOWN))))
        assertEquals(listOf(1L),ArtistLinks.episodePosts(posts,EpisodeKey(EpisodeKind.EPISODE,12),artist).map{it.id})
        assertEquals("show artist_a",ArtistLinks.selectedQuery(listOf("show","artist_a","show")))
    }
    @Test fun multipleQueriesRestoreTheirOwnPagesWithoutRepeatingRequestsAndScoreStaysCurrent()=runTest(dispatcher){
        val requests=mutableListOf<String>()
        val repo=object:PostRepository{override val pageSize=2;override suspend fun page(query:String,page:Int):PostPage{requests+="$query:$page";return PostPage(listOf(post(page.toLong())),2)}}
        val model=FeedViewModel(SavedStateHandle(),repo);advanceUntilIdle()
        val first=model.beginSearch("artist_a");advanceUntilIdle();model.loadMore();advanceUntilIdle()
        val second=model.beginSearch("artist_a show");advanceUntilIdle();model.updateScore(1,9)
        val count=requests.size
        model.activateSearch(first,"artist_a");advanceUntilIdle()
        assertEquals(listOf(1L,2L),model.state.value.posts.map{it.id});assertEquals(3,model.state.value.nextPage)
        assertEquals(9,model.state.value.posts.first().score);assertEquals(count,requests.size)
        model.activateSearch(second,"artist_a show");assertEquals("artist_a show",model.state.value.query)
        model.restoreBrowse();assertEquals("",model.state.value.query);assertEquals(1,model.state.value.posts.size)
    }
    @Test fun sampleSearchCannotFetchOutsideEpisodeAndMissingProcessCacheIsSafe()=runTest(dispatcher){
        var requests=0
        val repo=object:PostRepository{override suspend fun page(query:String,page:Int):PostPage{requests++;return PostPage(listOf(post()),1)}}
        val model=FeedViewModel(SavedStateHandle(),repo);advanceUntilIdle();val before=requests
        val sample=model.beginSampleSearch("show artist_a",listOf(post(),post()));model.refresh();model.loadMore();advanceUntilIdle()
        assertEquals(before,requests);assertEquals(1,model.state.value.posts.size);assertTrue(model.state.value.sampleOnly)
        model.restoreBrowse();model.activateSearch(sample,"show artist_a",true);assertEquals(1,model.state.value.posts.size)
        model.activateSearch("process-lost","show artist_a",true);assertTrue(model.state.value.posts.isEmpty());assertTrue(model.state.value.endReached)
        model.activateSearch("evicted","artist_b");advanceUntilIdle();assertEquals("artist_b",model.state.value.query);assertEquals(before+1,requests)
    }
    @Test fun cancelledDetailResponseCannotReplaceNewerSelection()=runTest(dispatcher){
        val waiting=CompletableDeferred<Unit>()
        val repo=object:PostRepository{override suspend fun page(query:String,page:Int):PostPage{
            if(query=="id:1")withContext(NonCancellable){waiting.await()}
            return PostPage(listOf(post(if(query=="id:1")1 else 2)),1)
        }}
        val model=FeedViewModel(SavedStateHandle(),repo);advanceUntilIdle();model.openId(1);runCurrent()
        model.open(post(2));waiting.complete(Unit);advanceUntilIdle();assertEquals(2L,model.selected.value!!.id)
    }
    @Test fun returningToAnOlderDetailKeepsItsSourceOrderAndConfirmedScore()=runTest(dispatcher){
        var requests=0
        val repo=object:PostRepository{override suspend fun page(query:String,page:Int):PostPage{requests++;return PostPage(listOf(post(1),post(2)),2)}}
        val model=FeedViewModel(SavedStateHandle(),repo);advanceUntilIdle()
        model.openLocal(post(2),listOf(post(2),post(1)));val detail=model.captureDetail()
        model.beginSearch("another_artist");advanceUntilIdle();model.open(post(1));model.updateScore(2,17)
        val before=requests;model.restoreDetail(2,detail)
        assertEquals(1L,model.adjacent(1)!!.id);assertNull(model.adjacent(-1));assertEquals(17,model.selected.value!!.score)
        assertTrue(model.usesLocalSnapshot());assertEquals(before,requests)
    }
    @Test fun interruptedRefreshRestartsPageOneWhenReturningToTheQuery()=runTest(dispatcher){
        val waiting=CompletableDeferred<Unit>();val calls=mutableListOf<Pair<String,Int>>()
        val repo=object:PostRepository{override val pageSize=1;override suspend fun page(query:String,page:Int):PostPage{
            calls+=query to page
            if(query=="artist_a" && calls.count{it.first==query}>=2)waiting.await()
            return PostPage(listOf(post()),1)
        }}
        val model=FeedViewModel(SavedStateHandle(),repo);advanceUntilIdle()
        val first=model.beginSearch("artist_a");advanceUntilIdle();model.refresh();runCurrent()
        model.beginSearch("artist_b");advanceUntilIdle();model.activateSearch(first,"artist_a");runCurrent()
        assertEquals("artist_a" to 1,calls.last());assertTrue(model.state.value.refreshing)
        waiting.complete(Unit);advanceUntilIdle();assertFalse(model.state.value.refreshing)
    }
    @Test fun startingSearchInvalidatesAnUnfinishedDetailRead()=runTest(dispatcher){
        val waiting=CompletableDeferred<Unit>()
        val repo=object:PostRepository{override suspend fun page(query:String,page:Int):PostPage{
            if(query=="id:1")withContext(NonCancellable){waiting.await()}
            return PostPage(listOf(post()),1)
        }}
        val model=FeedViewModel(SavedStateHandle(),repo);advanceUntilIdle();model.openId(1);runCurrent()
        model.beginSearch("artist_a");runCurrent();waiting.complete(Unit);advanceUntilIdle()
        assertNull(model.selected.value);assertEquals("artist_a",model.state.value.query)
    }

    @Test fun detailReturnUsesPagesLoadedAfterTheDetailWasFirstOpened()=runTest(dispatcher){
        var requests=0
        val repo=object:PostRepository{override val pageSize=2;override suspend fun page(query:String,page:Int):PostPage{requests++;return PostPage(listOf(post(page*2L-1),post(page*2L)),2)}}
        val model=FeedViewModel(SavedStateHandle(),repo);advanceUntilIdle()
        val first=model.beginSearch("artist_a");advanceUntilIdle();model.open(model.state.value.posts.last());val detail=model.captureDetail()
        model.loadMore();advanceUntilIdle();model.beginSearch("artist_b");advanceUntilIdle();val before=requests
        model.restoreDetail(3,detail)
        assertEquals(3L,model.selected.value!!.id);assertEquals(2L,model.adjacent(-1)!!.id);assertEquals(4L,model.adjacent(1)!!.id)
        assertEquals(before,requests);model.activateSearch(first,"artist_a");assertEquals(4,model.state.value.posts.size)
    }

}
