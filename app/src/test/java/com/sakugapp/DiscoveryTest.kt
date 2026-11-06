// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import com.sakugapp.data.*
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*

class DiscoveryTest {
    private fun post(id:Long=1,source:String?="#1",score:Int?=0,created:Long?=1609459200L,tags:List<Tag> = listOf(Tag("artist",TagCategory.ARTIST),Tag("show",TagCategory.COPYRIGHT)))=
        PostParser.posts(javaClass.getResource("/live/posts.json")!!.readText()).first().copy(id=id,source=source,score=score,createdAt=created,tags=tags)
    @Test fun explicitEpisodeFormsNormalize(){for(s in listOf("#1","#01","#001","Anime #0001","episode 1","Episode 01","ep1","ep. 1","EP 1"))assertEquals(s,EpisodeKey(EpisodeKind.EPISODE,1),SourceEpisodeParser.parse(s).group)}
    @Test fun explicitNumbersAreBoundedAndAmbiguityIsRejected(){for(s in listOf("2024","1080p","1920x1080","https://example.org/watch/12","https://example.org/#12","foo","#2024","episode 9999","#1 #2"))assertEquals(s,EpisodeKind.OTHER,SourceEpisodeParser.parse(s).group.kind)}
    @Test fun urlNumbersCannotOverrideExplicitOutsideText(){assertEquals(12,SourceEpisodeParser.parse("https://x.org/99 episode 12").group.number)}
    @Test fun missingIsSeparateFromOther(){assertEquals(EpisodeKind.MISSING,SourceEpisodeParser.parse(null).group.kind);assertEquals(EpisodeKind.MISSING,SourceEpisodeParser.parse(" ").group.kind);assertFalse(SourceEpisodeParser.parse("foo").reliable)}
    @Test fun opEdAndOvaKeepNumbers(){for((s,k,n)in listOf(Triple("OP",EpisodeKind.OP,null),Triple("OP1",EpisodeKind.OP,1),Triple("OP2",EpisodeKind.OP,2),Triple("Opening",EpisodeKind.OP,null),Triple("ED1",EpisodeKind.ED,1),Triple("Ending 2",EpisodeKind.ED,2),Triple("OVA 3",EpisodeKind.OVA,3)))assertEquals(s,EpisodeKey(k,n),SourceEpisodeParser.parse(s).group)}
    @Test fun specialsAndMoviesNeverGuessEpisodeNumbers(){for(s in listOf("Movie 2020","Film 1995"))assertEquals(EpisodeKey(EpisodeKind.MOVIE),SourceEpisodeParser.parse(s).group);for(s in listOf("PV","Trailer","Teaser"))assertEquals(EpisodeKind.PV,SourceEpisodeParser.parse(s).group.kind)}
    @Test fun groupsUseNaturalOrderAndDeduplicatePostIds(){val groups=SourceEpisodeParser.groups(listOf(post(1,"#10"),post(2,"#2"),post(2,"#02")));assertEquals(listOf(2,10),groups.keys.map{it.number});assertEquals(2,groups.values.sumOf{it.size})}
    @Test fun averagesUseValidScoresIncludingZero(){val stats=ArtistStatsCalculator.calculate("artist",listOf(post(1,score=0),post(2,score=10),post(3,score=null),post(1,score=100)));assertEquals(3,stats.count);assertEquals(2,stats.scoreCount);assertEquals(5.0,stats.mean!!,.0001)}
    @Test fun noScoreIsUnknownNotZero(){assertNull(ArtistStatsCalculator.calculate("artist",listOf(post(score=null))).mean)}
    @Test fun yearsAreUploadSecondsInUtcAndMissingDatesAreCounted(){val stats=ArtistStatsCalculator.calculate("artist",listOf(post(1,created=1609459200),post(2,created=1640995200),post(3,created=null),post(4,created=1609459200000)));assertEquals(mapOf(2021 to 1,2022 to 1),stats.years);assertEquals(2,stats.missingYears)}
    @Test fun repeatedTagsCountOnceAndMultipleCopyrightsBothCount(){val tags=listOf(Tag("artist",TagCategory.ARTIST),Tag("a",TagCategory.COPYRIGHT),Tag("a",TagCategory.COPYRIGHT),Tag("b",TagCategory.COPYRIGHT));val stats=ArtistStatsCalculator.calculate("artist",listOf(post(tags=tags)));assertEquals(listOf("a","b"),stats.copyrights.map{it.tag.name});assertTrue(stats.related.all{it.count==1 && it.percent==100.0})}
    @Test fun cooccurrencesSortByCountAndName(){val stats=ArtistStatsCalculator.calculate("artist",listOf(post(1,tags=listOf(Tag("b",TagCategory.OTHER))),post(2,tags=listOf(Tag("a",TagCategory.OTHER),Tag("b",TagCategory.OTHER)))));assertEquals(listOf("b","a"),stats.related.map{it.tag.name});assertEquals(50.0,stats.related.last().percent,.01)}
    private fun repo(source:PostRepository,clock:()->Long={1L})=DiscoveryRepository(source,{mapOf("artist" to TagCategory.ARTIST,"show" to TagCategory.COPYRIGHT)},clock)
    @Test fun pagingCapCacheRefreshAndDedup()=runBlocking{
        val calls=mutableListOf<Int>();val source=object:PostRepository{override val pageSize=2;override suspend fun page(query:String,page:Int):PostPage{calls+=page;return PostPage(listOf(post(page.toLong()),post(1)),2)}}
        val repo=repo(source);var value=repo.load("artist",TagCategory.ARTIST);assertEquals(1,value.pages)
        repeat(8){value=repo.load("artist",TagCategory.ARTIST)};assertEquals(5,value.pages);assertTrue(value.capped);assertEquals((1..5).toList(),calls);assertEquals(5,value.posts.size)
        repo.load("artist",TagCategory.ARTIST,refresh=true);assertEquals(1,calls.last())
    }
    @Test fun expiredCacheReloadsAndUnknownTypeNeverRequestsPosts()=runBlocking{
        var now=1L;var calls=0;val source=object:PostRepository{override suspend fun page(query:String,page:Int):PostPage{calls++;return PostPage(listOf(post()),1)}}
        val repo=repo(source){now};repo.load("artist",TagCategory.ARTIST);assertNotNull(repo.cached("artist",TagCategory.ARTIST));now+=600001;assertNull(repo.cached("artist",TagCategory.ARTIST))
        try{repo.load("artist",TagCategory.COPYRIGHT);fail()}catch(_:DataFailure){};assertEquals(1,calls)
    }
    @Test fun failureDoesNotCommitFailedPageAndRetryStartsAtNextPage()=runBlocking{
        var fail=true;val calls=mutableListOf<Int>();val source=object:PostRepository{override val pageSize=1;override suspend fun page(query:String,page:Int):PostPage{calls+=page;if(page==2 && fail)throw DataFailure("HTTP 429");return PostPage(listOf(post(page.toLong())),1)}}
        val repo=repo(source);try{repo.load("show",TagCategory.COPYRIGHT,3);fail()}catch(_:DataFailure){};assertEquals(1,repo.cached("show",TagCategory.COPYRIGHT)!!.pages);fail=false;repo.load("show",TagCategory.COPYRIGHT,1);assertEquals(listOf(1,2,2),calls)
    }
    @Test fun cancellationCannotMarkSampleComplete()=runBlocking{
        val source=object:PostRepository{override suspend fun page(query:String,page:Int):PostPage{delay(10000);return PostPage(emptyList(),0)}}
        val repo=repo(source);val task=launch{repo.load("artist",TagCategory.ARTIST)};delay(20);task.cancelAndJoin();assertNull(repo.cached("artist",TagCategory.ARTIST))
    }
    @Test fun combinedSearchRetainsBothRawTags(){assertEquals("artist show",SearchSyntax.add("artist","show","and"))}
    @Test fun profileNamesCountCodePointsAndRejectControls(){assertEquals("原画師🙂",ProfileRules.nickname(" 原画師🙂 "));assertEquals("🙂".repeat(20),ProfileRules.nickname("🙂".repeat(20)));for(s in listOf(" ","x\ny","a".repeat(21),"a\u202Eb"))assertThrows(IllegalArgumentException::class.java){ProfileRules.nickname(s)}}
}
