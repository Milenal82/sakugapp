// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import com.sakugapp.data.*
import com.sakugapp.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class QuizAndFavoritesTest {
    private fun post(id:Long=1)=PostParser.posts(javaClass.getResource("/live/posts.json")!!.readText()).first().copy(id=id,fileUrl="https://www.sakugabooru.com/data/test.mp4",fileExt="mp4",tags=listOf(Tag("a",TagCategory.ARTIST),Tag("show",TagCategory.COPYRIGHT)))
    private val artists=listOf("a","b","c","d","e")
    private val catalog=artists.associateWith{TagCategory.ARTIST}+mapOf("show" to TagCategory.COPYRIGHT,"other" to TagCategory.COPYRIGHT)
    @Test fun optionsAreUniqueExactAndCorrectPositionIsRandom(){
        val positions=(0..50).map{seed->val q=QuizGenerator.question(post(),artists,emptySet(),QuizSettings(),Random(seed))!!;assertEquals(4,q.options.toSet().size);assertEquals(1,q.options.count{it==q.correct});q.options.indexOf(q.correct)}.toSet()
        assertEquals(setOf(0,1,2,3),positions)
        assertNull(QuizGenerator.question(post(),listOf("a","b","b"),emptySet(),QuizSettings()))
    }
    @Test fun ineligiblePostsAndSubstringAttributionAreRejected(){
        for(p in listOf(post().copy(fileUrl=null),post().copy(fileExt="gif"),post().copy(tags=listOf(Tag("a",TagCategory.UNKNOWN))),post().copy(tags=post().tags+Tag("b",TagCategory.ARTIST)),post().copy(tags=post().tags+Tag("presumed",TagCategory.OTHER))))assertNull(QuizGenerator.question(p,artists,emptySet(),QuizSettings()))
        assertNull(QuizGenerator.question(post(),artists,setOf(1),QuizSettings()))
        assertNull(QuizGenerator.question(post(),artists,emptySet(),QuizSettings(custom=true,works=listOf("sho"))))
        assertNotNull(QuizGenerator.question(post(),artists,emptySet(),QuizSettings(custom=false,works=listOf("other"))))
    }
    @Test fun answerIsLockedAndAccuracyUsesActualCompletedCount(){
        val q=QuizGenerator.question(post(),artists,emptySet(),QuizSettings())!!
        val wrong=q.options.first{it!=q.correct};val first=QuizGenerator.answer(q,q.correct,emptyList());assertEquals(1,QuizGenerator.answer(q,wrong,first).size)
        val second=QuizGenerator.answer(q.copy(post=post(2)),wrong,first);assertEquals(50.0,QuizGenerator.accuracy(second)!!,.001);assertNull(QuizGenerator.accuracy(emptyList()))
    }
    @Test fun repositoryBoundsAttemptsAndIgnoresHiddenPoolForAllWorks()=runBlocking{
        val queries=mutableListOf<String>();val repo=QuizRepository(object:PostRepository{override suspend fun page(query:String,page:Int):PostPage{queries+=query;return PostPage(emptyList(),0)}},{catalog},Random(1))
        repo.start(QuizSettings(custom=false,works=listOf("other")))
        try{repo.next(emptySet());fail()}catch(e:DataFailure){assertEquals("quiz_insufficient",e.message)}
        assertEquals(12,queries.size);assertTrue(queries.none{it.startsWith("other ")})
        try{repo.start(QuizSettings(custom=true,works=listOf("a")));fail()}catch(_:DataFailure){}
    }
    @Test fun insufficientArtistCandidatesUseAtMostTwoVerifiedSupplementPages()=runBlocking{
        val calls=mutableListOf<Int>()
        val repo=QuizRepository(object:PostRepository{override suspend fun page(query:String,page:Int)=PostPage(listOf(post()),1)},{mapOf("a" to TagCategory.ARTIST)},Random(2),{page->calls+=page;if(page==1)listOf(DirectoryTag(2,"b",TagCategory.ARTIST,1),DirectoryTag(5,"not_an_artist",TagCategory.COPYRIGHT,1))else listOf(DirectoryTag(3,"c",TagCategory.ARTIST,1),DirectoryTag(4,"d",TagCategory.ARTIST,1))})
        repo.start(QuizSettings());assertEquals(listOf(1,2),calls);assertEquals(setOf("a","b","c","d"),repo.next(emptySet()).options.toSet())
    }
    @Test fun duplicateSupplementPagesFailBeforeFetchingQuizPosts()=runBlocking{
        var calls=0;var pages=0
        val repo=QuizRepository(object:PostRepository{override suspend fun page(query:String,page:Int):PostPage{calls++;return PostPage(emptyList(),0)}},{mapOf("a" to TagCategory.ARTIST)},Random(1),{pages++;listOf(DirectoryTag(1,"a",TagCategory.ARTIST,1))})
        try{repo.start(QuizSettings());fail()}catch(e:DataFailure){assertEquals("quiz_insufficient",e.message)}
        assertEquals(2,pages);assertEquals(0,calls)
    }
    @Test fun cancelledOldFetchCannotPopulateANewWorkPool()=runBlocking{
        val started=CompletableDeferred<Unit>();var first=true
        val source=object:PostRepository{override suspend fun page(query:String,page:Int):PostPage{
            if(first){first=false;started.complete(Unit);withContext(NonCancellable){delay(80)}}
            val tag=if(query.startsWith("other "))"other"else"show"
            return PostPage(listOf(post().copy(tags=listOf(Tag("a",TagCategory.ARTIST),Tag(tag,TagCategory.COPYRIGHT)))),1)
        }}
        val repo=QuizRepository(source,{catalog});repo.start(QuizSettings(custom=true,works=listOf("show")))
        val old=launch{repo.next(emptySet())};started.await();old.cancel()
        repo.start(QuizSettings(custom=true,works=listOf("other")));old.join()
        assertTrue(repo.next(emptySet()).post.tags.any{it.name=="other"})
    }
    @Test fun customPoolAndUsedIdsAreCheckedLocally()=runBlocking{
        val repo=QuizRepository(object:PostRepository{override suspend fun page(query:String,page:Int)=PostPage(listOf(post(1),post(2),post(3).copy(tags=listOf(Tag("a",TagCategory.ARTIST),Tag("other",TagCategory.COPYRIGHT)))),3)},{catalog})
        repo.start(QuizSettings(custom=true,works=listOf("show")));assertEquals(2L,repo.next(setOf(1)).post.id)
    }
    @Test fun favoriteMembershipUsesPersonalVoteNotGlobalScore()=runBlocking{
        val account=Account(1,"reader");var query="";var actual=1
        val source=WebsiteFavoritesRepository(object:PostRepository{override suspend fun page(q:String,page:Int):PostPage{query=q;return PostPage(listOf(post().copy(score=999)),1)}},{account},{actual})
        assertEquals(999,source.page(account,1,1).posts.single().score);assertEquals("vote:1:reader order:id_desc",query)
        try{source.page(account,3,1);fail()}catch(_:DataFailure){}
        actual=0;try{source.page(account,0,1);fail()}catch(_:DataFailure){}
        actual=2;assertEquals(1,source.page(account,0,1).posts.size);assertTrue(query.contains("vote:1..3:"))
    }
    @Test fun changedAccountsAndFailureNeverProduceFakeLists()=runBlocking{
        val source=WebsiteFavoritesRepository(object:PostRepository{override suspend fun page(query:String,page:Int):PostPage=error("Must not fetch another account")},{Account(2,"other")},{3})
        try{source.page(Account(1,"reader"),3,1);fail()}catch(_:DataFailure){}
    }
    @Test fun favoritesClearOnAccountChangeAndRejectOldResponses()=runTest{
        val dispatcher=StandardTestDispatcher(testScheduler);Dispatchers.setMain(dispatcher)
        try{val account=MutableStateFlow<Account?>(Account(1,"first"));val revision=MutableStateFlow(0)
            val source=object:WebsiteFavoritesSource{override suspend fun page(account:Account,grade:Int,page:Int):PostPage{delay(100);return PostPage(listOf(post(account.id)),1)}}
            val vm=WebsiteFavoritesViewModel(source,account,revision);vm.active(true);runCurrent();account.value=Account(2,"second");runCurrent();assertTrue(vm.state.value.posts.isEmpty());advanceUntilIdle();assertEquals(2L,vm.state.value.posts.single().id)
            account.value=null;runCurrent();assertTrue(vm.state.value.posts.isEmpty());vm.active(false)
        }finally{Dispatchers.resetMain()}
    }
}
